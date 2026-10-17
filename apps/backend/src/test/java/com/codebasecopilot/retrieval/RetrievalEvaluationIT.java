package com.codebasecopilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.TestProperties;
import com.codebasecopilot.TestcontainersConfiguration;
import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import com.codebasecopilot.eval.EmbeddingCache;
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
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
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
@Import({TestcontainersConfiguration.class, RetrievalEvaluationIT.CacheConfiguration.class})
class RetrievalEvaluationIT {

    static final Path REPOSITORY_ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path QUESTIONS = REPOSITORY_ROOT.resolve("eval/questions");
    static final Path RESULTS = REPOSITORY_ROOT.resolve("eval/results");

    private static final int CANDIDATES = 50;
    private static final Duration INDEX_TIMEOUT = Duration.ofMinutes(30);
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private LexicalSearch lexical;
    @Autowired private VectorSearch vector;
    @Autowired private PathSearch path;
    @Autowired private ObjectProvider<EmbeddingProvider> providers;

    private final List<Path> trees = new ArrayList<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class CacheConfiguration {

        @Bean
        static BeanPostProcessor embeddingCache(Environment environment) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    return bean instanceof EmbeddingProvider provider
                                    && !(bean instanceof EmbeddingCache)
                            ? new EmbeddingCache(provider, cacheDirectory(environment))
                            : bean;
                }
            };
        }

        private static Path cacheDirectory(Environment environment) {
            String configured = environment.getProperty("EVAL_CACHE_DIR");
            if (configured != null && !configured.isBlank()) {
                return Path.of(configured);
            }
            String dataDir =
                    environment.resolvePlaceholders("${DATA_DIR:~/.codebase-copilot}").strip();
            if (dataDir.equals("~") || dataDir.startsWith("~/")) {
                dataDir = System.getProperty("user.home") + dataDir.substring(1);
            }
            return Path.of(dataDir, "eval-cache");
        }
    }

    @AfterEach
    void removeTrees() throws IOException {
        for (Path tree : trees) {
            Directories.deleteRecursively(tree);
        }
    }

    @Test
    void evaluatesEveryCorpus() throws IOException {
        EmbeddingProvider provider = providers.getIfAvailable();
        UUID workspaceId = workspace();
        List<CorpusRun> corpora = new ArrayList<>();
        for (Path file : questionFiles()) {
            corpora.add(evaluate(QuestionSets.load(file), workspaceId, provider));
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
                        LocalDate.now().toString(), configName(model), config, corpora);

        Path written = EvaluationReport.write(RESULTS, run);
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
        Path source = set.verifiedDirectory(REPOSITORY_ROOT, System::getenv);
        Path tree = copy(source, set.corpus().name());
        UUID snapshotId = index(project(workspaceId, tree));
        if (provider != null) {
            assertThat(snapshotModel(snapshotId)).isEqualTo(provider.model());
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

        SnapshotFacts facts = facts(snapshotId);
        return new CorpusRun(
                set.corpus(),
                sha256(REPOSITORY_ROOT.resolve(set.corpus().manifest())),
                facts.chunkingVersion(),
                facts.files(),
                facts.chunks(),
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

    private static List<Path> questionFiles() throws IOException {
        String selected = System.getenv("EVAL_CORPORA");
        List<String> names =
                selected == null || selected.isBlank()
                        ? null
                        : Arrays.stream(selected.split(",")).map(String::strip).toList();
        List<Path> files;
        try (Stream<Path> list = Files.list(QUESTIONS)) {
            files = list.filter(f -> f.toString().endsWith(".yaml")).sorted().toList();
        }
        if (names == null) {
            return files;
        }
        List<Path> chosen =
                files.stream()
                        .filter(
                                f ->
                                        names.contains(
                                                f.getFileName().toString().replace(".yaml", "")))
                        .toList();
        assertThat(chosen).as("corpora in EVAL_CORPORA").hasSize(names.size());
        return chosen;
    }

    private static String configName(@Nullable ModelInfo model) {
        if (model == null) {
            return "lexical";
        }
        return (model.provider() + "-" + model.model()).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    // The corpus is copied into a local root, which is where the importer reads folders from.
    private Path copy(Path source, String name) throws IOException {
        Path tree =
                TestFileSystemInitializer.ROOTS_DIR.resolve(
                        "work/eval-" + name + "-" + UUID.randomUUID());
        trees.add(tree);
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path from : paths.toList()) {
                Path to = tree.resolve(source.relativize(from).toString());
                if (Files.isDirectory(from)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        return tree;
    }

    private UUID workspace() {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Avaliação\"}")
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(read(result, "$.id"));
    }

    private UUID project(UUID workspaceId, Path tree) {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/workspaces/{id}/projects", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name": "%s", "sourceType": "LOCAL_FOLDER", "rootKey": "work",
                                 "relativePath": "%s"}
                                """
                                        .formatted(tree.getFileName(), tree.getFileName()))
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(read(result, "$.id"));
    }

    private UUID index(UUID projectId) {
        MvcTestResult created =
                mvc.post().uri("/api/v1/projects/{id}/index-jobs", projectId).exchange();
        assertThat(created).hasStatus(HttpStatus.ACCEPTED);
        String jobId = read(created, "$.id");
        String status =
                await().atMost(INDEX_TIMEOUT)
                        .pollInterval(Duration.ofMillis(500))
                        .until(
                                () ->
                                        read(
                                                mvc.get()
                                                        .uri("/api/v1/index-jobs/{id}", jobId)
                                                        .exchange(),
                                                "$.status"),
                                TERMINAL::contains);
        assertThat(status).as("index job %s", jobId).isEqualTo("COMPLETED");
        return UUID.fromString(read(created, "$.snapshotId"));
    }

    private @Nullable ModelInfo snapshotModel(UUID snapshotId) {
        return jdbc.sql(
                        """
                        SELECT embedding_provider, embedding_model, embedding_dimensions
                        FROM snapshots WHERE id = :id
                        """)
                .param("id", snapshotId)
                .query(
                        (rs, rowNum) ->
                                rs.getString("embedding_provider") == null
                                        ? null
                                        : new ModelInfo(
                                                rs.getString("embedding_provider"),
                                                rs.getString("embedding_model"),
                                                rs.getInt("embedding_dimensions")))
                .single();
    }

    private record SnapshotFacts(String chunkingVersion, int files, int chunks) {}

    private SnapshotFacts facts(UUID snapshotId) {
        return jdbc.sql(
                        """
                        SELECT s.chunking_version,
                               (SELECT count(*) FROM snapshot_files f
                                WHERE f.snapshot_id = s.id AND f.content_hash IS NOT NULL)
                                   AS files,
                               (SELECT count(DISTINCT c.id)
                                FROM snapshot_files f
                                JOIN chunks c ON c.project_id = f.project_id
                                             AND c.content_hash = f.content_hash
                                             AND c.chunking_version = s.chunking_version
                                WHERE f.snapshot_id = s.id) AS chunks
                        FROM snapshots s WHERE s.id = :id
                        """)
                .param("id", snapshotId)
                .query(
                        (rs, rowNum) ->
                                new SnapshotFacts(
                                        rs.getString("chunking_version"),
                                        rs.getInt("files"),
                                        rs.getInt("chunks")))
                .single();
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

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
