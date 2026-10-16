package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// The missing-hash query must reach a snapshot's chunks and their embeddings through indexes
// instead of scanning every project's rows.
@IntegrationTest
class ChunkEmbeddingPlanIT {

    private static final int PROJECTS = 100;
    private static final int FILES_PER_PROJECT = 200;
    private static final int CHUNKS_PER_FILE = 10;

    @Autowired private JdbcClient jdbc;

    @AfterEach
    void cleanUp() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void missingChunkHashesUsesIndexesOnALargeDatabase() {
        DatabaseCleaner.truncateAll(jdbc);
        UUID workspaceId = insertWorkspace(jdbc);
        UUID snapshotId = null;
        for (int i = 0; i < PROJECTS; i++) {
            UUID projectId = insertZipProject(jdbc, workspaceId);
            snapshotId = insertProjectRows(projectId);
        }
        jdbc.sql(
                        """
                        INSERT INTO chunk_embeddings (chunk_hash, provider, model, dimensions,
                                                      embedding, created_at)
                        SELECT DISTINCT chunk_hash, 'fake', 'small', 2, '[0.6,-0.8]'::vector, now()
                        FROM chunks
                        WHERE ordinal > 0
                        """)
                .update();
        jdbc.sql("ANALYZE").update();

        List<String> plan =
                jdbc.sql("EXPLAIN " + ChunkEmbeddingRepository.MISSING_CHUNK_HASHES)
                        .param("snapshotId", snapshotId)
                        .param("provider", "fake")
                        .param("model", "small")
                        .param("dimensions", 2)
                        .query(String.class)
                        .list();

        assertThat(String.join("\n", plan))
                .doesNotContain("Seq Scan on chunks")
                .doesNotContain("Seq Scan on chunk_embeddings")
                .doesNotContain("Seq Scan on snapshot_files");
    }

    private UUID insertProjectRows(UUID projectId) {
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");
        jdbc.sql(
                        """
                        INSERT INTO file_contents (project_id, content_hash, content, size_bytes,
                                                   line_count, created_at)
                        SELECT :projectId, 'h' || g, 'x', 1, 1, now()
                        FROM generate_series(1, :files) g
                        """)
                .param("projectId", projectId)
                .param("files", FILES_PER_PROJECT)
                .update();
        jdbc.sql(
                        """
                        INSERT INTO chunks (id, project_id, content_hash, chunking_version,
                                            ordinal, start_line, end_line, content, chunk_hash,
                                            token_count_estimate)
                        SELECT gen_random_uuid(), :projectId, 'h' || g, 'lines-v1', o, 1, 1, 'x',
                               md5(:projectId::text || '-' || g || '-' || o), 1
                        FROM generate_series(1, :files) g, generate_series(0, :chunks - 1) o
                        """)
                .param("projectId", projectId)
                .param("files", FILES_PER_PROJECT)
                .param("chunks", CHUNKS_PER_FILE)
                .update();
        jdbc.sql(
                        """
                        INSERT INTO snapshot_files (snapshot_id, project_id, relative_path,
                                                    status, content_hash, language, size_bytes)
                        SELECT :snapshotId, :projectId, 'f' || g, 'INDEXED', 'h' || g, 'text', 1
                        FROM generate_series(1, :files) g
                        """)
                .param("snapshotId", snapshotId)
                .param("projectId", projectId)
                .param("files", FILES_PER_PROJECT)
                .update();
        return snapshotId;
    }
}
