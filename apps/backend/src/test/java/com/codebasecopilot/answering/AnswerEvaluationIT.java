package com.codebasecopilot.answering;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.TestProperties;
import com.codebasecopilot.TestcontainersConfiguration;
import com.codebasecopilot.eval.AnswerEvaluationReport;
import com.codebasecopilot.eval.AnswerEvaluationReport.CorpusRun;
import com.codebasecopilot.eval.AnswerEvaluationReport.QuestionRun;
import com.codebasecopilot.eval.AnswerEvaluationReport.Usage;
import com.codebasecopilot.eval.AnswerQuality.Answer;
import com.codebasecopilot.eval.AnswerQuality.Evidence;
import com.codebasecopilot.eval.EmbeddingCache;
import com.codebasecopilot.eval.EvalCaches;
import com.codebasecopilot.eval.EvalCorpora;
import com.codebasecopilot.eval.GenerationCache;
import com.codebasecopilot.eval.QuestionSet;
import com.codebasecopilot.eval.QuestionSet.Question;
import com.codebasecopilot.eval.QuestionSets;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.GenerationModel;
import com.codebasecopilot.providers.GenerationProperties;
import com.codebasecopilot.providers.GenerationProvider;
import com.codebasecopilot.retrieval.SearchMode;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Answer evaluation (P10 of Etapa 6), outside ./mvnw verify. Indexes each corpus like the
// retrieval evaluation (D-062), asks every question through POST /questions, one conversation
// each, and writes <date>-answers-<provider>-<model>-<effort>-<mode>.json and .md.
//
// EVAL_GENERATION_PROVIDER=fake (default) only tests the harness: the results go to
// target/eval-results and are not versioned. EVAL_GENERATION_PROVIDER=openai sends the question
// and the evidence to the chat provider (key from GENERATION_API_KEY, model and reasoning effort
// from app.generation.*) and writes to eval/results. EVAL_PROVIDER chooses the embeddings, as in
// the retrieval evaluation; EVAL_RETRIEVAL_MODE defaults to HYBRID with embeddings and to LEXICAL
// without. Vectors and answers are cached in DATA_DIR/eval-cache (or EVAL_CACHE_DIR): a second
// run pays nothing and repeats the same answers. EVAL_CORPORA limits the corpora and
// EVAL_RESULTS_DIR overrides the output folder.
// ./mvnw verify -Dtest.excluded-groups= -Dgroups=eval -Dtest=none
//     -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AnswerEvaluationIT
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
            "app.generation.provider=${EVAL_GENERATION_PROVIDER:fake}"
        })
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestFileSystemInitializer.class)
@Import({TestcontainersConfiguration.class, EvalCaches.class})
class AnswerEvaluationIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path UNVERSIONED_RESULTS = Path.of("target/eval-results").toAbsolutePath();

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private ObjectProvider<EmbeddingProvider> embeddings;
    @Autowired private GenerationProvider generator;
    @Autowired private GenerationProperties generation;
    @Autowired private AnsweringProperties answering;
    @Autowired private PricingProperties pricing;

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
    void answersEveryQuestion() throws IOException {
        EmbeddingProvider embedding = embeddings.getIfAvailable();
        SearchMode mode = mode(embedding != null);
        GenerationCache cache = (GenerationCache) generator;
        UUID workspaceId = corpora.workspace();
        List<CorpusRun> runs = new ArrayList<>();
        for (Path file : EvalCorpora.questionFiles()) {
            runs.add(evaluate(QuestionSets.load(file), workspaceId, mode, cache));
        }

        GenerationModel model = generator.model();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("generationProvider", model.provider());
        config.put("generationModel", model.model());
        config.put("reasoningEffort", generation.reasoningEffort());
        config.put("maxOutputTokens", generation.maxOutputTokens());
        config.put("promptVersion", PromptBuilder.VERSION);
        config.put("embeddingProvider", embedding == null ? null : embedding.model().provider());
        config.put("embeddingModel", embedding == null ? null : embedding.model().model());
        config.put("retrievalMode", mode.name());
        config.put("candidates", answering.candidates());
        config.put("maxEvidence", AnswerService.DEFAULT_MAX_EVIDENCE);
        config.put("maxContextTokens", answering.maxContextTokens());
        config.put("maxEvidencePerFile", answering.maxEvidencePerFile());
        config.put("priceInputPerMillionUsd", pricing.inputPerMillion());
        config.put("priceOutputPerMillionUsd", pricing.outputPerMillion());
        String name =
                ("answers-"
                                + model.provider()
                                + "-"
                                + model.model()
                                + (generation.reasoningEffort().isBlank()
                                        ? ""
                                        : "-" + generation.reasoningEffort())
                                + "-"
                                + mode.name().toLowerCase(Locale.ROOT))
                        .replaceAll("[^A-Za-z0-9._-]", "_");
        AnswerEvaluationReport.Run run =
                new AnswerEvaluationReport.Run(LocalDate.now().toString(), name, config, runs);

        Path written = AnswerEvaluationReport.write(resultsDirectory(model), run);
        System.out.println("Answer evaluation results written to " + written);
        GenerationCache.Usage usage = cache.usage();
        System.out.printf(
                Locale.ROOT,
                "Answer cache: %d hits, %d misses, %d input and %d output tokens billed%n",
                usage.hits(),
                usage.misses(),
                usage.inputTokens(),
                usage.outputTokens());
        if (embedding instanceof EmbeddingCache vectors) {
            EmbeddingCache.Usage used = vectors.usage();
            System.out.printf(
                    Locale.ROOT,
                    "Embedding cache: %d hits, %d misses, %d tokens billed%n",
                    used.hits(),
                    used.misses(),
                    used.tokens());
        }
    }

    private CorpusRun evaluate(
            QuestionSet set, UUID workspaceId, SearchMode mode, GenerationCache cache)
            throws IOException {
        EvalCorpora.Indexed indexed = corpora.index(set, workspaceId);
        List<QuestionRun> questions = new ArrayList<>();
        for (Question question : set.questions()) {
            int hits = cache.usage().hits();
            JsonNode answer = ask(indexed, question.question(), mode);
            boolean cached = cache.usage().hits() > hits;
            questions.add(new QuestionRun(question, answer(answer), usage(answer, cached)));
        }
        return new CorpusRun(
                set.corpus(),
                indexed.manifestSha256(),
                indexed.chunkingVersion(),
                indexed.files(),
                indexed.chunks(),
                questions);
    }

    private JsonNode ask(EvalCorpora.Indexed indexed, String question, SearchMode mode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", question);
        body.put("snapshotId", indexed.snapshotId());
        body.put("options", Map.of("retrievalMode", mode.name()));
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/projects/{id}/questions", indexed.projectId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(body))
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        try {
            return JSON.readTree(result.getResponse().getContentAsString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Answer answer(JsonNode answer) {
        List<Evidence> evidence = new ArrayList<>();
        for (JsonNode e : answer.get("evidence")) {
            List<String> paths = new ArrayList<>(List.of(e.get("path").stringValue()));
            e.get("otherPaths").forEach(path -> paths.add(path.stringValue()));
            evidence.add(
                    new Evidence(
                            e.get("citationLabel").stringValue(),
                            List.copyOf(paths),
                            e.get("startLine").intValue(),
                            e.get("endLine").intValue(),
                            integer(e.get("citedStartLine")),
                            integer(e.get("citedEndLine")),
                            e.get("validationStatus").stringValue(),
                            e.get("snippet").stringValue()));
        }
        JsonNode error = answer.get("error");
        return new Answer(
                answer.get("status").stringValue(),
                answer.get("answer").isNull() ? null : answer.get("answer").stringValue(),
                answer.get("invalidCitationCount").intValue(),
                List.copyOf(evidence),
                error.isNull() ? null : error.get("code").stringValue());
    }

    private static Usage usage(JsonNode answer, boolean cached) {
        JsonNode metrics = answer.get("metrics");
        JsonNode cost = metrics.get("estimatedCost");
        return new Usage(
                integer(metrics.get("inputTokens")),
                integer(metrics.get("outputTokens")),
                cost.isNull() ? null : cost.decimalValue(),
                number(metrics.get("latencyMs")),
                number(metrics.get("generationMs")),
                cached);
    }

    private static @Nullable Integer integer(JsonNode node) {
        return node == null || node.isNull() ? null : node.intValue();
    }

    private static @Nullable Long number(JsonNode node) {
        return node == null || node.isNull() ? null : node.longValue();
    }

    private static SearchMode mode(boolean withEmbeddings) {
        String configured = System.getenv("EVAL_RETRIEVAL_MODE");
        if (configured != null && !configured.isBlank()) {
            return SearchMode.valueOf(configured.strip());
        }
        return withEmbeddings ? SearchMode.HYBRID : SearchMode.LEXICAL;
    }

    // A run with the fake provider only tests the harness and is not versioned.
    private static Path resultsDirectory(GenerationModel model) {
        String configured = System.getenv("EVAL_RESULTS_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return model.provider().equals("fake") ? UNVERSIONED_RESULTS : EvalCorpora.RESULTS;
    }
}
