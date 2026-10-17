package com.codebasecopilot.answering;

import com.codebasecopilot.answering.CitationValidator.Check;
import com.codebasecopilot.history.AnswerEvidence;
import com.codebasecopilot.history.AnswerHistory;
import com.codebasecopilot.history.AnswerMetrics;
import com.codebasecopilot.history.AnswerOutcome;
import com.codebasecopilot.history.AnswerStatus;
import com.codebasecopilot.history.NewQuestion;
import com.codebasecopilot.history.PendingAnswer;
import com.codebasecopilot.history.StoredAnswer;
import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.providers.GenerationFailure;
import com.codebasecopilot.providers.GenerationModel;
import com.codebasecopilot.providers.GenerationProvider;
import com.codebasecopilot.providers.GenerationResult;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.retrieval.Retrieval;
import com.codebasecopilot.retrieval.RetrievedChunk;
import com.codebasecopilot.retrieval.SearchMode;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.error.FieldViolation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

// The flow of P8 (Etapa 6): every check before anything is persisted; then a short transaction
// for the question, the provider calls outside any transaction, and a short one for the outcome.
// After the question is stored, provider failures become a FAILED answer, never an API error,
// so the question stays in the history. Question, answer, prompt and code are never logged.
@Service
class AnswerService {

    static final int MAX_QUESTION_CHARS = Retrieval.MAX_QUESTION_CHARS;
    static final int DEFAULT_MAX_EVIDENCE = 8;
    static final int MAX_EVIDENCE = 20;
    static final SearchMode DEFAULT_MODE = SearchMode.HYBRID;

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    private final ProjectService projects;
    private final AnswerHistory history;
    private final Retrieval retrieval;
    private final AnsweringProperties properties;
    private final PricingProperties pricing;
    private final @Nullable GenerationProvider generator;

    AnswerService(
            ProjectService projects,
            AnswerHistory history,
            Retrieval retrieval,
            AnsweringProperties properties,
            PricingProperties pricing,
            ObjectProvider<GenerationProvider> generator) {
        this.projects = projects;
        this.history = history;
        this.retrieval = retrieval;
        this.properties = properties;
        this.pricing = pricing;
        this.generator = generator.getIfAvailable();
    }

    record Question(
            @Nullable String text,
            @Nullable UUID snapshotId,
            @Nullable UUID conversationId,
            @Nullable Integer maxEvidence,
            @Nullable String retrievalMode) {}

    private record Valid(String text, int maxEvidence, SearchMode mode) {}

    // Precedence: VALIDATION_FAILED, PROJECT_NOT_FOUND, CONVERSATION_NOT_FOUND, the snapshot and
    // embedding checks of the search (D-060), GENERATION_NOT_AVAILABLE.
    StoredAnswer ask(UUID projectId, Question question) {
        long started = System.nanoTime();
        Valid valid = validate(question);
        projects.find(projectId);
        UUID conversationId = question.conversationId();
        if (conversationId != null) {
            history.requireConversation(projectId, conversationId);
        }
        Retrieval.Prepared prepared =
                retrieval.prepare(
                        new Retrieval.Request(
                                projectId,
                                valid.text(),
                                question.snapshotId(),
                                valid.mode(),
                                properties.candidates()));
        GenerationProvider generator = this.generator;
        if (generator == null) {
            throw new ApiException(ErrorCode.GENERATION_NOT_AVAILABLE);
        }

        PendingAnswer pending =
                history.start(
                        new NewQuestion(
                                projectId,
                                conversationId,
                                valid.text(),
                                prepared.snapshotId(),
                                prepared.mode().name(),
                                PromptBuilder.VERSION));
        Run run = new Run(started);
        AnswerOutcome outcome = answer(valid, prepared, generator, run);
        boolean completed = history.complete(pending.answerId(), outcome);
        log.info(
                "Answer {} of project {} on snapshot {} in {} mode: {}{}, {} evidence, {} input"
                        + " and {} output tokens, durations {} ms{}",
                pending.answerId(),
                projectId,
                prepared.snapshotId(),
                prepared.mode(),
                outcome.status(),
                outcome.errorCode() == null ? "" : " " + outcome.errorCode(),
                outcome.evidence().size(),
                outcome.metrics().inputTokens(),
                outcome.metrics().outputTokens(),
                run.durations,
                completed ? "" : " (no longer pending, not stored)");
        // Gone when the project was removed while the providers answered.
        return history.findAnswer(pending.answerId())
                .orElseThrow(() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private AnswerOutcome answer(
            Valid valid, Retrieval.Prepared prepared, GenerationProvider generator, Run run) {
        run.metadata.put("embeddingModel", model(prepared.embeddingModel()));
        long start = System.nanoTime();
        Retrieval.Result retrieved;
        try {
            retrieved = retrieval.retrieve(prepared);
        } catch (ApiException e) {
            AnswerErrorCode code = AnswerErrorCode.ofEmbedding(e.code());
            if (code == null) {
                throw e;
            }
            run.time("retrieval", start);
            return run.failed(code, null, List.of(), run.metrics(null, null, pricing));
        }
        run.time("retrieval", start);
        if (retrieved.embeddingMillis() != null) {
            run.durations.put("questionEmbedding", retrieved.embeddingMillis());
        }
        run.metadata.put("lexicalVersion", retrieved.lexicalVersion());
        run.metadata.put("questionEmbeddingTokens", retrieved.embeddingTokens());
        run.metadata.put("candidates", retrieved.candidates());
        run.metadata.put("retrievedChunks", retrieved.chunks().size());

        List<ContextEvidence> evidence =
                ContextAssembler.assemble(
                        valid.text(),
                        retrieved.chunks(),
                        new ContextAssembler.Limits(
                                valid.maxEvidence(),
                                properties.maxContextTokens(),
                                properties.maxEvidencePerFile()));
        if (evidence.isEmpty()) {
            return run.outcome(
                    AnswerStatus.ABSTAINED,
                    null,
                    List.of(AnswerWarning.NO_EVIDENCE.name()),
                    0,
                    null,
                    List.of(),
                    run.metrics(null, null, pricing));
        }

        GenerationModel model = generator.model();
        start = System.nanoTime();
        GenerationResult result;
        try {
            result =
                    generator.generate(
                            PromptBuilder.build(valid.text(), evidence, PromptBuilder.newNonce()));
        } catch (GenerationFailure e) {
            run.time("generation", start);
            return run.failed(
                    AnswerErrorCode.of(e.type()),
                    model,
                    notCited(evidence),
                    run.metrics(null, null, pricing));
        }
        run.time("generation", start);
        run.metadata.put("reasoningTokens", result.reasoningTokens());

        start = System.nanoTime();
        CitationValidator.Outcome validated = CitationValidator.validate(result.output(), evidence);
        run.time("validation", start);
        AnswerMetrics metrics = run.metrics(result.inputTokens(), result.outputTokens(), pricing);
        if (!(validated instanceof CitationValidator.Valid answer)) {
            return run.failed(
                    AnswerErrorCode.GENERATION_OUTPUT_INVALID, model, notCited(evidence), metrics);
        }
        List<AnswerEvidence> stored = new ArrayList<>();
        for (ContextEvidence item : evidence) {
            stored.add(copy(item, answer.checks().getOrDefault(item.label(), Check.NOT_CITED)));
        }
        return run.outcome(
                answer.status(),
                answer.text().isBlank() ? null : answer.text(),
                answer.warnings().stream().map(Enum::name).toList(),
                answer.invalidCitationCount(),
                model,
                stored,
                metrics);
    }

    private static Valid validate(Question question) {
        List<FieldViolation> violations = new ArrayList<>();
        String text = question.text();
        if (text == null || text.isBlank()) {
            violations.add(new FieldViolation("question", "validation.question.required"));
        } else if (text.length() > MAX_QUESTION_CHARS) {
            violations.add(new FieldViolation("question", "validation.question.too-long"));
        }
        int maxEvidence =
                question.maxEvidence() == null ? DEFAULT_MAX_EVIDENCE : question.maxEvidence();
        if (maxEvidence < 1 || maxEvidence > MAX_EVIDENCE) {
            violations.add(
                    new FieldViolation(
                            "options.maxEvidence",
                            "validation.question.max-evidence-out-of-range"));
        }
        SearchMode mode = mode(question.retrievalMode());
        if (mode == null) {
            violations.add(
                    new FieldViolation(
                            "options.retrievalMode", "validation.question.retrieval-mode-invalid"));
        }
        if (!violations.isEmpty() || text == null || mode == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, violations);
        }
        return new Valid(text, maxEvidence, mode);
    }

    // Exact names only, like the search (D-060).
    private static @Nullable SearchMode mode(@Nullable String mode) {
        if (mode == null) {
            return DEFAULT_MODE;
        }
        for (SearchMode candidate : SearchMode.values()) {
            if (candidate.name().equals(mode)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<AnswerEvidence> notCited(List<ContextEvidence> evidence) {
        return evidence.stream().map(item -> copy(item, Check.NOT_CITED)).toList();
    }

    private static AnswerEvidence copy(ContextEvidence item, Check check) {
        RetrievedChunk chunk = item.chunk();
        return new AnswerEvidence(
                chunk.chunkId(),
                item.label(),
                chunk.paths().getFirst(),
                chunk.paths().subList(1, chunk.paths().size()),
                chunk.contentHash(),
                chunk.startLine(),
                chunk.endLine(),
                check.citedStartLine(),
                check.citedEndLine(),
                chunk.content(),
                chunk.rankLexical(),
                chunk.rankVector(),
                chunk.rankPath(),
                chunk.rank(),
                check.validation());
    }

    private static @Nullable Map<String, Object> model(@Nullable ModelInfo model) {
        if (model == null) {
            return null;
        }
        return Map.of(
                "provider", model.provider(),
                "model", model.model(),
                "dimensions", model.dimensions());
    }

    // What one request measured; the metadata never holds question, answer or code text.
    private static final class Run {

        private final long started;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private final Map<String, Long> durations = new LinkedHashMap<>();

        Run(long started) {
            this.started = started;
            metadata.put("durationsMs", durations);
        }

        void time(String step, long start) {
            durations.put(step, millisSince(start));
        }

        AnswerMetrics metrics(
                @Nullable Long inputTokens,
                @Nullable Long outputTokens,
                PricingProperties pricing) {
            return CostEstimate.metrics(millisSince(started), inputTokens, outputTokens, pricing);
        }

        AnswerOutcome failed(
                AnswerErrorCode code,
                @Nullable GenerationModel model,
                List<AnswerEvidence> evidence,
                AnswerMetrics metrics) {
            return new AnswerOutcome(
                    AnswerStatus.FAILED,
                    null,
                    code.name(),
                    List.of(),
                    0,
                    model == null ? null : model.provider(),
                    model == null ? null : model.model(),
                    metadata,
                    metrics,
                    evidence);
        }

        AnswerOutcome outcome(
                AnswerStatus status,
                @Nullable String text,
                List<String> warnings,
                int invalidCitations,
                @Nullable GenerationModel model,
                List<AnswerEvidence> evidence,
                AnswerMetrics metrics) {
            return new AnswerOutcome(
                    status,
                    text,
                    null,
                    warnings,
                    invalidCitations,
                    model == null ? null : model.provider(),
                    model == null ? null : model.model(),
                    metadata,
                    metrics,
                    evidence);
        }

        private static long millisSince(long start) {
            return (System.nanoTime() - start) / 1_000_000;
        }
    }
}
