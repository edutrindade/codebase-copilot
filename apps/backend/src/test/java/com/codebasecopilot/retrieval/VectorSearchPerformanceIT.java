package com.codebasecopilot.retrieval;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.providers.ModelInfo;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// Manual measurement of the exact search (D-049), outside ./mvnw verify:
// ./mvnw verify -Dtest.excluded-groups= -Dgroups=performance -Dtest=none
//     -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VectorSearchPerformanceIT
@Tag("performance")
@IntegrationTest
class VectorSearchPerformanceIT {

    private static final int FILES = 5_000;
    private static final int CHUNKS_PER_FILE = 10;
    private static final int WARMUP = 5;
    private static final int RUNS = 50;
    private static final int LIMIT = 10;
    private static final ModelInfo MODEL = new ModelInfo("fake", "perf-model", 1536);

    @Autowired private JdbcClient jdbc;
    @Autowired private VectorSearch search;

    @AfterEach
    void clean() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void exactSearchOverFiftyThousandChunks() {
        DatabaseCleaner.truncateAll(jdbc);
        UUID projectId = insertZipProject(jdbc, insertWorkspace(jdbc));
        UUID snapshotId = insertSnapshot(jdbc, projectId, "READY");
        populate(projectId, snapshotId);
        Random random = new Random(42);

        for (int i = 0; i < WARMUP; i++) {
            search.search(snapshotId, MODEL, randomVector(random), LIMIT);
        }
        long[] millis = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            assertThat(search.search(snapshotId, MODEL, randomVector(random), LIMIT))
                    .hasSize(LIMIT);
            millis[i] = (System.nanoTime() - start) / 1_000_000;
        }

        Arrays.sort(millis);
        System.out.printf(
                Locale.ROOT,
                "Exact vector search over %d chunks (%d dimensions): mean %.1f ms, p95 %d ms,"
                        + " max %d ms (%d runs)%n",
                FILES * CHUNKS_PER_FILE,
                MODEL.dimensions(),
                Arrays.stream(millis).average().orElseThrow(),
                millis[(int) Math.ceil(RUNS * 0.95) - 1],
                millis[RUNS - 1],
                RUNS);
    }

    private void populate(UUID projectId, UUID snapshotId) {
        jdbc.sql(
                        """
                        INSERT INTO file_contents (project_id, content_hash, content, size_bytes,
                                                   line_count, created_at)
                        SELECT :projectId, 'h' || f, 'content', 1, 60, now()
                        FROM generate_series(1, :files) f
                        """)
                .param("projectId", projectId)
                .param("files", FILES)
                .update();
        jdbc.sql(
                        """
                        INSERT INTO snapshot_files (snapshot_id, project_id, relative_path,
                                                    status, content_hash, language, size_bytes)
                        SELECT :snapshotId, :projectId, 'src/f' || f || '.txt', 'INDEXED',
                               'h' || f, 'text', 1
                        FROM generate_series(1, :files) f
                        """)
                .param("snapshotId", snapshotId)
                .param("projectId", projectId)
                .param("files", FILES)
                .update();
        jdbc.sql(
                        """
                        INSERT INTO chunks (id, project_id, content_hash, chunking_version,
                                            ordinal, start_line, end_line, content, chunk_hash,
                                            token_count_estimate)
                        SELECT gen_random_uuid(), :projectId, 'h' || f, 'lines-v1', o,
                               o * 6 + 1, o * 6 + 6, 'chunk ' || f || '-' || o,
                               'c' || f || '-' || o, 10
                        FROM generate_series(1, :files) f, generate_series(0, :chunks - 1) o
                        """)
                .param("projectId", projectId)
                .param("files", FILES)
                .param("chunks", CHUNKS_PER_FILE)
                .update();
        // The WHERE references the outer row so each vector is drawn anew.
        jdbc.sql(
                        """
                        INSERT INTO chunk_embeddings (chunk_hash, provider, model, dimensions,
                                                      embedding, created_at)
                        SELECT c.chunk_hash, :provider, :model, :dimensions,
                               CAST(ARRAY(SELECT random() - 0.5
                                          FROM generate_series(1, :dimensions)
                                          WHERE c.ordinal >= 0) AS vector),
                               now()
                        FROM chunks c
                        WHERE c.project_id = :projectId
                        """)
                .param("projectId", projectId)
                .param("provider", MODEL.provider())
                .param("model", MODEL.model())
                .param("dimensions", MODEL.dimensions())
                .update();
        jdbc.sql("ANALYZE").update();
    }

    private static float[] randomVector(Random random) {
        float[] vector = new float[MODEL.dimensions()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = random.nextFloat() - 0.5f;
        }
        return vector;
    }
}
