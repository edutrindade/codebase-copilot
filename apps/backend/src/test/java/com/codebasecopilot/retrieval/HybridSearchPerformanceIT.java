package com.codebasecopilot.retrieval;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.retrieval.RrfFusion.Channel;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// Manual measurement of the lexical and hybrid search and of the size of chunk_lexical (D-056,
// D-059), outside ./mvnw verify:
// ./mvnw verify -Dtest.excluded-groups= -Dgroups=performance -Dtest=none
//     -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=HybridSearchPerformanceIT
@Tag("performance")
@IntegrationTest
class HybridSearchPerformanceIT {

    private static final int FILES = 5_000;
    private static final int CHUNKS_PER_FILE = 10;
    // Words per chunk (about 2 KB of text) and per identifier_terms, drawn from a vocabulary
    // skewed towards its first words, so a few terms are common and most are rare.
    private static final int WORDS_PER_CHUNK = 300;
    private static final int TERMS_PER_CHUNK = 40;
    private static final int VOCABULARY = 5_000;
    private static final int WARMUP = 5;
    private static final int RUNS = 50;
    private static final int LIMIT = 10;
    private static final int CANDIDATES = 50;
    private static final ModelInfo MODEL = new ModelInfo("fake", "perf-model", 1536);

    @Autowired private JdbcClient jdbc;
    @Autowired private LexicalSearch lexical;
    @Autowired private VectorSearch vector;
    @Autowired private PathSearch path;

    @AfterEach
    void clean() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void lexicalAndHybridSearchOverFiftyThousandChunks() {
        DatabaseCleaner.truncateAll(jdbc);
        UUID projectId = insertZipProject(jdbc, insertWorkspace(jdbc));
        UUID snapshotId = insertSnapshot(jdbc, projectId, "READY");
        populate(projectId, snapshotId);
        Random random = new Random(42);

        for (int i = 0; i < WARMUP; i++) {
            hybrid(snapshotId, question(random), randomVector(random));
        }
        long[] lexicalMillis = new long[RUNS];
        long[] rareMillis = new long[RUNS];
        long[] hybridMillis = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            String question = question(random);
            lexicalMillis[i] =
                    millis(
                            () ->
                                    assertThat(lexical.search(snapshotId, question, LIMIT))
                                            .isNotEmpty());
            String rare = rareQuestion(random);
            rareMillis[i] = millis(() -> lexical.search(snapshotId, rare, LIMIT));
            float[] queryVector = randomVector(random);
            hybridMillis[i] =
                    millis(
                            () ->
                                    assertThat(hybrid(snapshotId, question, queryVector))
                                            .hasSize(LIMIT));
        }

        report("Lexical search, one term in every chunk (limit " + LIMIT + ")", lexicalMillis);
        report("Lexical search, rare terms only (limit " + LIMIT + ")", rareMillis);
        explain(snapshotId, question(random));
        report(
                "Hybrid search (3 channels x "
                        + CANDIDATES
                        + " candidates + RRF, without the"
                        + " embedding call)",
                hybridMillis);
        System.out.println(
                jdbc.sql(
                                """
                                SELECT format('chunk_lexical: %s rows, table %s, indexes %s;'
                                              || ' chunks: table %s',
                                              (SELECT count(*) FROM chunk_lexical),
                                              pg_size_pretty(pg_table_size('chunk_lexical')),
                                              pg_size_pretty(pg_indexes_size('chunk_lexical')),
                                              pg_size_pretty(pg_table_size('chunks')))
                                """)
                        .query(String.class)
                        .single());
    }

    private List<RrfFusion.RankedHit> hybrid(UUID snapshotId, String question, float[] query) {
        return RrfFusion.fuse(
                Map.of(
                        Channel.VECTOR, vector.search(snapshotId, MODEL, query, CANDIDATES),
                        Channel.LEXICAL, lexical.search(snapshotId, question, CANDIDATES),
                        Channel.PATH,
                                path.search(
                                        snapshotId,
                                        question,
                                        PathSearch.DEFAULT_THRESHOLD,
                                        CANDIDATES)),
                RrfFusion.Parameters.DEFAULT,
                LIMIT);
    }

    // One common term, two random ones and a file name, as in "where is wX used in FileN.java".
    private static String question(Random random) {
        return "where is w%d used with w%d and w%d in File%d.java"
                .formatted(
                        1 + random.nextInt(20),
                        1 + random.nextInt(VOCABULARY),
                        1 + random.nextInt(VOCABULARY),
                        1 + random.nextInt(FILES));
    }

    // Words from the rare end of the vocabulary, each in a few dozen chunks.
    private static String rareQuestion(Random random) {
        return "where is w%d used with w%d"
                .formatted(VOCABULARY - random.nextInt(1000), VOCABULARY - random.nextInt(1000));
    }

    private void explain(UUID snapshotId, String question) {
        jdbc.sql("EXPLAIN (ANALYZE, BUFFERS) " + LexicalSearch.SEARCH)
                .param("snapshotId", snapshotId)
                .param("version", IdentifierTokenizer.VERSION)
                .param("query", LexicalSearch.tsquery(IdentifierTokenizer.queryTerms(question)))
                .param("limit", LIMIT)
                .query(String.class)
                .list()
                .forEach(System.out::println);
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
                        SELECT :snapshotId, :projectId,
                               'src/module' || (f % 50) || '/File' || f || '.java', 'INDEXED',
                               'h' || f, 'java', 1
                        FROM generate_series(1, :files) f
                        """)
                .param("snapshotId", snapshotId)
                .param("projectId", projectId)
                .param("files", FILES)
                .update();
        // The subquery references the outer row so each chunk draws its own words.
        jdbc.sql(
                        """
                        INSERT INTO chunks (id, project_id, content_hash, chunking_version,
                                            ordinal, start_line, end_line, content, chunk_hash,
                                            token_count_estimate)
                        SELECT gen_random_uuid(), :projectId, 'h' || f, 'lines-v1', o,
                               o * 6 + 1, o * 6 + 6,
                               (SELECT string_agg(
                                           'w' || (1 + floor(:vocabulary
                                                             * power(random(), 3)))::int, ' ')
                                FROM generate_series(1, :words) WHERE o >= 0),
                               'c' || f || '-' || o, 500
                        FROM generate_series(1, :files) f, generate_series(0, :chunks - 1) o
                        """)
                .param("projectId", projectId)
                .param("files", FILES)
                .param("chunks", CHUNKS_PER_FILE)
                .param("words", WORDS_PER_CHUNK)
                .param("vocabulary", VOCABULARY)
                .update();
        // Same tsv expression as ChunkLexicalRepository.
        jdbc.sql(
                        """
                        INSERT INTO chunk_lexical (chunk_id, lexical_version, identifier_terms,
                                                   tsv)
                        SELECT t.id, :version, t.terms,
                               setweight(to_tsvector('simple', t.terms), 'A')
                                   || setweight(to_tsvector('simple', t.content), 'B')
                        FROM (SELECT c.id, c.content,
                                     (SELECT string_agg(
                                                 'w' || (1 + floor(:vocabulary
                                                                   * power(random(), 3)))::int,
                                                 ' ')
                                      FROM generate_series(1, :terms)
                                      WHERE c.ordinal >= 0) AS terms
                              FROM chunks c WHERE c.project_id = :projectId) t
                        """)
                .param("projectId", projectId)
                .param("version", IdentifierTokenizer.VERSION)
                .param("vocabulary", VOCABULARY)
                .param("terms", TERMS_PER_CHUNK)
                .update();
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

    private static long millis(Runnable search) {
        long start = System.nanoTime();
        search.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void report(String what, long[] millis) {
        Arrays.sort(millis);
        System.out.printf(
                Locale.ROOT,
                "%s over %d chunks: mean %.1f ms, p95 %d ms, max %d ms (%d runs)%n",
                what,
                FILES * CHUNKS_PER_FILE,
                Arrays.stream(millis).average().orElseThrow(),
                millis[(int) Math.ceil(RUNS * 0.95) - 1],
                millis[RUNS - 1],
                RUNS);
    }

    private static float[] randomVector(Random random) {
        float[] vector = new float[MODEL.dimensions()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = random.nextFloat() - 0.5f;
        }
        return vector;
    }
}
