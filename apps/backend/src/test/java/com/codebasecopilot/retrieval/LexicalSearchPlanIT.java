package com.codebasecopilot.retrieval;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// The lexical and path queries must reach one snapshot's rows through indexes instead of
// scanning every project's rows (D-057, D-058).
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LexicalSearchPlanIT {

    private static final int PROJECTS = 100;
    private static final int FILES_PER_PROJECT = 200;
    private static final int CHUNKS_PER_FILE = 10;

    @Autowired private JdbcClient jdbc;

    private UUID snapshotId;

    @BeforeAll
    void populate() {
        DatabaseCleaner.truncateAll(jdbc);
        UUID workspaceId = insertWorkspace(jdbc);
        for (int i = 0; i < PROJECTS; i++) {
            snapshotId = insertProjectRows(insertZipProject(jdbc, workspaceId));
        }
        jdbc.sql(
                        """
                        INSERT INTO chunk_lexical (chunk_id, lexical_version, identifier_terms, tsv)
                        SELECT id, :version, 'term' || ordinal,
                               setweight(to_tsvector('simple', 'term' || ordinal), 'A')
                                   || setweight(to_tsvector('simple', content), 'B')
                        FROM chunks
                        """)
                .param("version", IdentifierTokenizer.VERSION)
                .update();
        jdbc.sql("ANALYZE").update();
    }

    @AfterAll
    void cleanUp() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void lexicalSearchUsesIndexesOnALargeDatabase() {
        String plan =
                plan(
                        jdbc.sql("EXPLAIN " + LexicalSearch.SEARCH)
                                .param("snapshotId", snapshotId)
                                .param("version", IdentifierTokenizer.VERSION)
                                .param("query", "'term3' | 'shared'")
                                .param("limit", 10));

        assertThat(plan)
                .doesNotContain("Seq Scan on chunk_lexical")
                .doesNotContain("Seq Scan on chunks")
                .doesNotContain("Seq Scan on snapshot_files");
    }

    // The planner may read the snapshot's files by primary key or the paths by the trigram
    // index, which covers every snapshot; both stay off a sequential scan.
    @Test
    void pathSearchUsesIndexesOnALargeDatabase() {
        String plan =
                plan(
                        jdbc.sql("EXPLAIN " + PathSearch.SEARCH)
                                .param("snapshotId", snapshotId)
                                .param("terms", "src/File17.java")
                                .param("threshold", PathSearch.DEFAULT_THRESHOLD)
                                .param("limit", 10));

        assertThat(plan)
                .doesNotContain("Seq Scan on chunks")
                .doesNotContain("Seq Scan on snapshot_files");
    }

    @Test
    void fileNameLookupStaysInsideTheSnapshot() {
        String plan =
                plan(
                        jdbc.sql("EXPLAIN " + PathSearch.MATCHING_NAMES)
                                .param("snapshotId", snapshotId)
                                .param("names", "File17\nOther"));

        assertThat(plan).doesNotContain("Seq Scan on snapshot_files");
    }

    private static String plan(JdbcClient.StatementSpec explain) {
        List<String> lines = explain.query(String.class).list();
        return String.join("\n", lines);
    }

    private UUID insertProjectRows(UUID projectId) {
        UUID snapshot = insertSnapshot(jdbc, projectId, "READY");
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
                        SELECT gen_random_uuid(), :projectId, 'h' || g, 'lines-v1', o, 1, 1,
                               'word' || g || ' shared text ' || md5(g || '-' || o),
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
                        SELECT :snapshotId, :projectId, 'src/File' || g || '.java', 'INDEXED',
                               'h' || g, 'java', 1
                        FROM generate_series(1, :files) g
                        """)
                .param("snapshotId", snapshot)
                .param("projectId", projectId)
                .param("files", FILES_PER_PROJECT)
                .update();
        return snapshot;
    }
}
