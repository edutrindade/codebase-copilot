package com.codebasecopilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.TestProperties;
import com.codebasecopilot.TestcontainersConfiguration;
import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import com.codebasecopilot.eval.EmbeddingCache;
import com.codebasecopilot.eval.EvalCaches;
import com.codebasecopilot.eval.EvalCorpora;
import com.codebasecopilot.eval.EvaluationReport;
import com.codebasecopilot.eval.EvaluationReport.CorpusRun;
import com.codebasecopilot.eval.EvaluationReport.QuestionRun;
import com.codebasecopilot.eval.QuestionSet;
import com.codebasecopilot.eval.QuestionSet.Question;
import com.codebasecopilot.eval.QuestionSets;
import com.codebasecopilot.eval.RetrievalMetrics;
import com.codebasecopilot.eval.RetrievalMetrics.RetrievedChunk;
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
// fails the run when its results (date and latencies aside) differ from an earlier one. Corpus
// folders:
// eval/README.md.
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

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private LexicalSearch lexical;
    @Autowired private VectorSearch vector;
    @Autowired private PathSearch path;
    @Autowired private ObjectProvider<EmbeddingProvider> providers;

    private EvalCorpora corpora;

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
        EvaluationReport.Run run =
                new EvaluationReport.Run(
                        LocalDate.now().toString(), configName(model), config, runs);

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
                                () ->
                                        vector.search(
                                                snapshotId, provider.model(), query, CANDIDATES)));
            }
            lists.put(
                    Channel.LEXICAL,
                    timed("LEXICAL", latency, () -> lexical.search(snapshotId, text, CANDIDATES)));
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
                                            CANDIDATES)));
            questions.add(new QuestionRun(question, variants(lists, provider != null)));
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
            Map<Channel, List<SearchHit>> lists, boolean withVector) {
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

    private static List<RetrievedChunk> fused(
            Map<Channel, List<SearchHit>> lists, Channel... channels) {
        Map<Channel, List<SearchHit>> selected = new EnumMap<>(Channel.class);
        for (Channel channel : channels) {
            selected.put(channel, lists.get(channel));
        }
        return chunks(
                RrfFusion.fuse(selected, RrfFusion.Parameters.DEFAULT, RetrievalMetrics.DEPTH));
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

    private static String configName(@Nullable ModelInfo model) {
        if (model == null) {
            return "lexical";
        }
        return (model.provider() + "-" + model.model()).replaceAll("[^A-Za-z0-9._-]", "_");
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
