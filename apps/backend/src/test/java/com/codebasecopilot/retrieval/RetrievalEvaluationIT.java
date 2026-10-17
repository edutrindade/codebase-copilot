package com.codebasecopilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.TestProperties;
import com.codebasecopilot.TestcontainersConfiguration;
import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import com.codebasecopilot.codeanalysis.PathTerms;
import com.codebasecopilot.eval.EmbeddingCache;
import com.codebasecopilot.eval.EvalCaches;
import com.codebasecopilot.eval.EvalChunking;
import com.codebasecopilot.eval.EvalCorpora;
import com.codebasecopilot.eval.EvaluationReport;
import com.codebasecopilot.eval.EvaluationReport.CorpusRun;
import com.codebasecopilot.eval.EvaluationReport.QuestionRun;
import com.codebasecopilot.eval.QuestionSet;
import com.codebasecopilot.eval.QuestionSet.Question;
import com.codebasecopilot.eval.QuestionSets;
import com.codebasecopilot.eval.RetrievalMetrics;
import com.codebasecopilot.eval.RetrievalMetrics.RetrievedChunk;
import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.retrieval.RrfFusion.Channel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

// Retrieval evaluation (D-062), outside ./mvnw verify. Indexes each corpus through the real
// pipeline, runs each question once per channel and fuses the variants in memory (D-059), then
// writes eval/results/<date>-<config>.json and .md. Nothing of the corpus is executed.
//
// EVAL_PROVIDER=none (default) measures only the lexical and path channels and sends nothing out;
// EVAL_PROVIDER=openai sends the corpora and the questions to the provider (key from
// EMBEDDING_API_KEY), with every vector cached in DATA_DIR/eval-cache (or EVAL_CACHE_DIR), so a
// second run costs nothing. EVAL_CORPORA=fixture,click limits the corpora; EVAL_COMPARE_WITH=<json>
// fails the run when its results (date and latencies aside) differ from an earlier one.
// EVAL_CHUNKING=<max lines>:<overlap lines> indexes with other chunking values, and
// EVAL_SWEEP=fusion adds the fusion of every channel with one parameter changed at a time
// (P3 of Etapa 8). Corpus folders: eval/README.md.
// ./mvnw verify -Dtest.excluded-groups= -Dgroups=eval -Dtest=none
//     -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RetrievalEvaluationIT
@Tag("eval")
@SpringBootTest(
        properties = {
            TestProperties.CONFIG_LOCATION,
            TestProperties.NO_PROJECTS_ROOT,
            TestProperties.DEFAULT_ROOT_KEY,
            TestProperties.DEFAULT_ROOT_LABEL,
            TestProperties.DEFAULT_ROOT_PATH,
            TestProperties.WORK_ROOT_KEY,
            TestProperties.WORK_ROOT_LABEL,
            TestProperties.WORK_ROOT_PATH,
            TestProperties.INDEX_POLL_INTERVAL,
            "app.embeddings.provider=${EVAL_PROVIDER:none}",
            "app.generation.provider=none"
        })
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestFileSystemInitializer.class)
@Import({TestcontainersConfiguration.class, EvalCaches.class})
class RetrievalEvaluationIT {

    private static final int CANDIDATES = 50;

    private static final List<Integer> SWEEP_K = List.of(10, 30, 100);
    private static final List<Double> SWEEP_WEIGHTS = List.of(0.5, 2.0);
    private static final List<Integer> SWEEP_CANDIDATES = List.of(20, 100);
    private static final List<Double> SWEEP_THRESHOLDS = List.of(0.6, 0.7, 0.9);

    private static final boolean SWEEP = sweep(System.getenv("EVAL_SWEEP"));

    // The path channel with file names matching only the files of that name, instead of every
    // path that has the name as a word (the hono regression of F1): path terms as in PathSearch,
    // file names by equality, with similarity 1.
    private static final String PATH_BY_FILE_NAME =
            """
            WITH matched AS (
                SELECT relative_path, max(similarity) AS similarity
                FROM (
                    SELECT f.relative_path, strict_word_similarity(t.term, f.relative_path)
                               AS similarity
                    FROM unnest(string_to_array(:terms, E'\\n')) AS t(term)
                    JOIN snapshot_files f ON t.term <<% f.relative_path
                    WHERE f.snapshot_id = :snapshotId
                      AND f.content_hash IS NOT NULL
                      AND strict_word_similarity(t.term, f.relative_path) >= :threshold
                    UNION ALL
                    SELECT f.relative_path, 1.0
                    FROM snapshot_files f
                    WHERE f.snapshot_id = :snapshotId
                      AND f.content_hash IS NOT NULL
                      AND lower(regexp_replace(f.relative_path, '^.*/|\\.[^.]*$', '', 'g'))
                          = ANY (string_to_array(lower(:names), E'\\n'))
                ) m
                GROUP BY relative_path
            ),
            """
                    + PathSearch.SEARCH.substring(PathSearch.SEARCH.indexOf("per_chunk AS"));

    @TestBean private LineChunker.Parameters chunking;

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private LexicalSearch lexical;
    @Autowired private VectorSearch vector;
    @Autowired private PathSearch path;
    @Autowired private ObjectProvider<EmbeddingProvider> providers;

    private EvalCorpora corpora;

    static LineChunker.Parameters chunking() {
        return EvalChunking.fromEnvironment();
    }

    @BeforeEach
    void setUp() {
        corpora = new EvalCorpora(mvc, jdbc);
    }

    @AfterEach
    void removeTrees() throws IOException {
        corpora.removeTrees();
    }

    @Test
    void evaluatesEveryCorpus() throws IOException {
        EmbeddingProvider provider = providers.getIfAvailable();
        UUID workspaceId = corpora.workspace();
        List<CorpusRun> runs = new ArrayList<>();
        for (Path file : EvalCorpora.questionFiles()) {
            runs.add(evaluate(QuestionSets.load(file), workspaceId, provider));
        }

        ModelInfo model = provider == null ? null : provider.model();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("provider", model == null ? null : model.provider());
        config.put("model", model == null ? null : model.model());
        config.put("dimensions", model == null ? null : model.dimensions());
        config.put("lexicalVersion", IdentifierTokenizer.VERSION);
        config.put("candidatesPerChannel", CANDIDATES);
        config.put("rrfK", RrfFusion.Parameters.DEFAULT.k());
        config.put("rrfWeights", "1 em todos os canais");
        config.put("pathThreshold", PathSearch.DEFAULT_THRESHOLD);
        config.put("rankingDepth", RetrievalMetrics.DEPTH);
        if (!chunking.equals(LineChunker.V1)) {
            config.put(
                    "chunking",
                    "%d linhas, sobreposição de %d"
                            .formatted(chunking.maxLines(), chunking.overlapLines()));
        }
        if (SWEEP) {
            config.put(
                    "sweep",
                    "fusão de todos os canais com um parâmetro alterado por vez: k %s, pesos %s,"
                                    .formatted(SWEEP_K, SWEEP_WEIGHTS)
                            + " candidatos por canal %s, limiar de caminhos %s"
                                    .formatted(SWEEP_CANDIDATES, SWEEP_THRESHOLDS));
        }
        EvaluationReport.Run run =
                new EvaluationReport.Run(
                        LocalDate.now().toString(), configName(model, chunking), config, runs);

        Path written = EvaluationReport.write(EvalCorpora.RESULTS, run);
        System.out.println("Evaluation results written to " + written);
        if (provider instanceof EmbeddingCache cache) {
            EmbeddingCache.Usage usage = cache.usage();
            System.out.printf(
                    Locale.ROOT,
                    "Embedding cache: %d hits, %d misses, %d tokens billed%n",
                    usage.hits(),
                    usage.misses(),
                    usage.tokens());
        }
        String compareWith = System.getenv("EVAL_COMPARE_WITH");
        if (compareWith != null && !compareWith.isBlank()) {
            assertThat(withoutLatencies(Files.readString(written)))
                    .as("results compared with %s", compareWith)
                    .isEqualTo(withoutLatencies(Files.readString(Path.of(compareWith))));
        }
    }

    private CorpusRun evaluate(
            QuestionSet set, UUID workspaceId, @Nullable EmbeddingProvider provider)
            throws IOException {
        EvalCorpora.Indexed indexed = corpora.index(set, workspaceId);
        UUID snapshotId = indexed.snapshotId();
        if (provider != null) {
            assertThat(corpora.snapshotModel(snapshotId)).isEqualTo(provider.model());
        }
        assertThat(indexed.chunkingVersion()).isEqualTo(chunking.version());
        int fetched = SWEEP ? Math.max(CANDIDATES, SWEEP_CANDIDATES.getLast()) : CANDIDATES;

        Map<String, List<Long>> latency = new LinkedHashMap<>();
        List<QuestionRun> questions = new ArrayList<>();
        for (Question question : set.questions()) {
            if (question.mustAbstain()) {
                continue;
            }
            Map<Channel, List<SearchHit>> lists = new EnumMap<>(Channel.class);
            String text = question.question();
            if (provider != null) {
                float[] query =
                        timed(
                                "EMBEDDING",
                                latency,
                                () -> provider.embed(List.of(text)).vectors().getFirst());
                lists.put(
                        Channel.VECTOR,
                        timed(
                                "VECTOR",
                                latency,
                                () -> vector.search(snapshotId, provider.model(), query, fetched)));
            }
            lists.put(
                    Channel.LEXICAL,
                    timed("LEXICAL", latency, () -> lexical.search(snapshotId, text, fetched)));
            lists.put(
                    Channel.PATH,
                    timed(
                            "PATH",
                            latency,
                            () ->
                                    path.search(
                                            snapshotId,
                                            text,
                                            PathSearch.DEFAULT_THRESHOLD,
                                            fetched)));
            Map<String, List<RetrievedChunk>> variants = variants(lists, provider != null);
            if (SWEEP) {
                Map<String, List<SearchHit>> paths = new LinkedHashMap<>();
                for (double threshold : SWEEP_THRESHOLDS) {
                    paths.put(
                            "threshold=" + threshold,
                            path.search(snapshotId, text, threshold, CANDIDATES));
                }
                paths.put("names=file", pathByFileName(snapshotId, text));
                variants.putAll(sweep(lists, paths, provider != null));
            }
            questions.add(new QuestionRun(question, variants));
        }

        return new CorpusRun(
                set.corpus(),
                indexed.manifestSha256(),
                indexed.chunkingVersion(),
                indexed.files(),
                indexed.chunks(),
                questions,
                latency);
    }

    // The search modes (D-060) and the hybrid without each optional channel, from the same lists.
    private static Map<String, List<RetrievedChunk>> variants(
            Map<Channel, List<SearchHit>> fetched, boolean withVector) {
        Map<Channel, List<SearchHit>> lists = truncated(fetched, CANDIDATES);
        Map<String, List<RetrievedChunk>> variants = new LinkedHashMap<>();
        variants.put("LEXICAL", single(Channel.LEXICAL, lists));
        if (withVector) {
            variants.put("VECTOR", single(Channel.VECTOR, lists));
        }
        variants.put("LEXICAL_PATH", fused(lists, Channel.LEXICAL, Channel.PATH));
        if (withVector) {
            variants.put("LEXICAL_VECTOR", fused(lists, Channel.LEXICAL, Channel.VECTOR));
            variants.put("HYBRID", fused(lists, Channel.LEXICAL, Channel.VECTOR, Channel.PATH));
        }
        return variants;
    }

    private static List<RetrievedChunk> single(
            Channel channel, Map<Channel, List<SearchHit>> lists) {
        return chunks(
                RrfFusion.single(channel, lists.get(channel)).stream()
                        .limit(RetrievalMetrics.DEPTH)
                        .toList());
    }

    // The fusion of every channel measured (LEXICAL_PATH without vectors, HYBRID with them), each
    // variant with one parameter changed from the defaults (P3 of Etapa 8). Scaling every weight
    // keeps the order, so without vectors only the path weight varies.
    private static Map<String, List<RetrievedChunk>> sweep(
            Map<Channel, List<SearchHit>> fetched,
            Map<String, List<SearchHit>> paths,
            boolean withVector) {
        String base = withVector ? "HYBRID" : "LEXICAL_PATH";
        Channel[] channels =
                withVector
                        ? new Channel[] {Channel.LEXICAL, Channel.VECTOR, Channel.PATH}
                        : new Channel[] {Channel.LEXICAL, Channel.PATH};
        Map<Channel, List<SearchHit>> lists = truncated(fetched, CANDIDATES);
        Map<String, List<RetrievedChunk>> variants = new LinkedHashMap<>();
        for (int k : SWEEP_K) {
            variants.put(
                    base + " k=" + k,
                    fused(lists, new RrfFusion.Parameters(k, Map.of()), channels));
        }
        for (Channel channel : withVector ? channels : new Channel[] {Channel.PATH}) {
            for (double weight : SWEEP_WEIGHTS) {
                variants.put(
                        base + " " + channel + "=" + weight,
                        fused(
                                lists,
                                new RrfFusion.Parameters(
                                        RrfFusion.Parameters.DEFAULT.k(), Map.of(channel, weight)),
                                channels));
            }
        }
        for (int candidates : SWEEP_CANDIDATES) {
            variants.put(
                    base + " candidates=" + candidates,
                    fused(truncated(fetched, candidates), RrfFusion.Parameters.DEFAULT, channels));
        }
        paths.forEach(
                (variant, hits) -> {
                    Map<Channel, List<SearchHit>> withThreshold = new EnumMap<>(lists);
                    withThreshold.put(Channel.PATH, hits);
                    variants.put(
                            base + " " + variant,
                            fused(withThreshold, RrfFusion.Parameters.DEFAULT, channels));
                });
        return variants;
    }

    private List<SearchHit> pathByFileName(UUID snapshotId, String question) {
        PathTerms.Terms terms = PathTerms.of(question);
        if (terms.paths().isEmpty() && terms.names().isEmpty()) {
            return List.of();
        }
        return jdbc.sql(PATH_BY_FILE_NAME)
                .param("snapshotId", snapshotId)
                .param("terms", String.join("\n", terms.paths()))
                .param("names", String.join("\n", terms.names()))
                .param("threshold", PathSearch.DEFAULT_THRESHOLD)
                .param("limit", CANDIDATES)
                .query(SnapshotChunks::hit)
                .list();
    }

    private static Map<Channel, List<SearchHit>> truncated(
            Map<Channel, List<SearchHit>> lists, int candidates) {
        Map<Channel, List<SearchHit>> truncated = new EnumMap<>(Channel.class);
        lists.forEach(
                (channel, hits) ->
                        truncated.put(channel, hits.subList(0, Math.min(candidates, hits.size()))));
        return truncated;
    }

    private static List<RetrievedChunk> fused(
            Map<Channel, List<SearchHit>> lists, Channel... channels) {
        return fused(lists, RrfFusion.Parameters.DEFAULT, channels);
    }

    private static List<RetrievedChunk> fused(
            Map<Channel, List<SearchHit>> lists,
            RrfFusion.Parameters parameters,
            Channel... channels) {
        Map<Channel, List<SearchHit>> selected = new EnumMap<>(Channel.class);
        for (Channel channel : channels) {
            selected.put(channel, lists.get(channel));
        }
        return chunks(RrfFusion.fuse(selected, parameters, RetrievalMetrics.DEPTH));
    }

    private static List<RetrievedChunk> chunks(List<RrfFusion.RankedHit> ranked) {
        return ranked.stream()
                .map(
                        r ->
                                new RetrievedChunk(
                                        r.hit().paths(), r.hit().startLine(), r.hit().endLine()))
                .toList();
    }

    private static <T> T timed(String channel, Map<String, List<Long>> latency, Supplier<T> call) {
        long start = System.nanoTime();
        T result = call.get();
        latency.computeIfAbsent(channel, c -> new ArrayList<>())
                .add((System.nanoTime() - start) / 1_000_000);
        return result;
    }

    private static String configName(@Nullable ModelInfo model, LineChunker.Parameters chunking) {
        String name =
                model == null
                        ? "lexical"
                        : (model.provider() + "-" + model.model())
                                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (!chunking.equals(LineChunker.V1)) {
            name += "-lines-" + chunking.maxLines() + "-" + chunking.overlapLines();
        }
        return SWEEP ? name + "-fusion" : name;
    }

    private static boolean sweep(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (!value.strip().equals("fusion")) {
            throw new IllegalArgumentException("EVAL_SWEEP must be fusion or absent");
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> withoutLatencies(String json) {
        Map<String, Object> root = JsonMapper.builder().build().readValue(json, Map.class);
        root.remove("date");
        for (Object corpus : (List<Object>) root.get("corpora")) {
            ((Map<String, Object>) corpus).remove("latencyMillis");
        }
        return root;
    }
}
