package com.codebasecopilot.answering;

import com.codebasecopilot.history.AnswerEvidence;
import com.codebasecopilot.history.AnswerMetrics;
import com.codebasecopilot.history.AnswerStatus;
import com.codebasecopilot.history.StoredAnswer;
import com.codebasecopilot.history.StoredEvidence;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Respostas",
        description = "Perguntas sobre o código do projeto, respondidas com evidências citadas.")
class AnswerController {

    private static final Locale LOCALE = Locale.of("pt", "BR");

    private final AnswerService service;
    private final MessageSource messages;

    AnswerController(AnswerService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @PostMapping("/projects/{projectId}/questions")
    @ApiErrors({
        ErrorCode.PROJECT_NOT_FOUND,
        ErrorCode.CONVERSATION_NOT_FOUND,
        ErrorCode.SNAPSHOT_NOT_FOUND,
        ErrorCode.SNAPSHOT_NOT_SEARCHABLE,
        ErrorCode.SNAPSHOT_LEXICAL_MISMATCH,
        ErrorCode.EMBEDDINGS_NOT_AVAILABLE,
        ErrorCode.SNAPSHOT_EMBEDDING_MISMATCH,
        ErrorCode.GENERATION_NOT_AVAILABLE
    })
    @Operation(
            operationId = "askQuestion",
            summary = "Responde a uma pergunta com evidências do código",
            description =
                    "Síncrono: responde quando a geração termina. Sem snapshotId, usa o snapshot"
                            + " ativo; sem conversationId, abre uma conversa. A pergunta e os"
                            + " trechos escolhidos como evidência são enviados ao provedor de"
                            + " geração configurado (e a pergunta ao de embeddings, nos modos"
                            + " VECTOR e HYBRID). Depois que a pergunta é registrada, falhas dos"
                            + " provedores não viram erro HTTP: a resposta é criada com status"
                            + " FAILED e error.code, e a pergunta fica no histórico.")
    ResponseEntity<AnswerResponse> ask(
            @PathVariable UUID projectId, @RequestBody QuestionRequest request) {
        OptionsRequest options = request.options();
        StoredAnswer answer =
                service.ask(
                        projectId,
                        new AnswerService.Question(
                                request.question(),
                                request.snapshotId(),
                                request.conversationId(),
                                options == null ? null : options.maxEvidence(),
                                options == null ? null : options.retrievalMode()));
        return ResponseEntity.created(URI.create("/api/v1/answers/" + answer.id()))
                .body(response(answer));
    }

    private AnswerResponse response(StoredAnswer answer) {
        String text = answer.text();
        if (text == null && answer.status() == AnswerStatus.ABSTAINED) {
            text = message("answer.abstained");
        }
        String errorCode = answer.errorCode();
        String provider = answer.generationProvider();
        String modelName = answer.generationModel();
        Map<String, Object> durations = durations(answer.retrievalMetadata());
        AnswerMetrics metrics = answer.metrics();
        return new AnswerResponse(
                answer.id(),
                answer.conversationId(),
                answer.snapshotId(),
                answer.question(),
                answer.status().name(),
                text,
                answer.warnings().stream()
                        .map(code -> warning(code, answer.invalidCitationCount()))
                        .toList(),
                answer.invalidCitationCount(),
                errorCode == null
                        ? null
                        : new ErrorResponse(errorCode, message("answer.error." + errorCode)),
                answer.evidence().stream().map(AnswerController::evidence).toList(),
                provider == null || modelName == null
                        ? null
                        : new ModelResponse(provider, modelName),
                new RetrievalResponse(answer.retrievalMode(), answer.promptVersion()),
                new MetricsResponse(
                        metrics.latencyMs(),
                        millis(durations, "retrieval"),
                        millis(durations, "generation"),
                        metrics.inputTokens(),
                        metrics.outputTokens(),
                        metrics.estimatedCost(),
                        metrics.costCurrency(),
                        metrics.costStatus().name()),
                answer.createdAt(),
                answer.completedAt());
    }

    private WarningResponse warning(String code, int invalidCitations) {
        return new WarningResponse(
                code,
                messages.getMessage(
                        "answer.warning." + code, new Object[] {invalidCitations}, LOCALE));
    }

    private static EvidenceResponse evidence(StoredEvidence stored) {
        AnswerEvidence evidence = stored.evidence();
        return new EvidenceResponse(
                stored.id(),
                evidence.citationLabel(),
                evidence.relativePath(),
                evidence.otherPaths(),
                evidence.startLine(),
                evidence.endLine(),
                evidence.citedStartLine(),
                evidence.citedEndLine(),
                evidence.snippet(),
                evidence.validation().name(),
                evidence.finalRank(),
                new RanksResponse(
                        evidence.rankLexical(), evidence.rankVector(), evidence.rankPath()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> durations(@Nullable Map<String, Object> metadata) {
        if (metadata != null && metadata.get("durationsMs") instanceof Map<?, ?> durations) {
            return (Map<String, Object>) durations;
        }
        return Map.of();
    }

    private static @Nullable Long millis(Map<String, Object> durations, String step) {
        return durations.get(step) instanceof Number value ? value.longValue() : null;
    }

    private String message(String key) {
        return messages.getMessage(key, null, LOCALE);
    }

    // retrievalMode is a string so an unknown value is a VALIDATION_FAILED on the field, not a
    // malformed body.
    record QuestionRequest(
            @Schema(minLength = 1, maxLength = AnswerService.MAX_QUESTION_CHARS)
                    @Nullable String question,
            @Schema(nullable = true, description = "Ausente: snapshot ativo do projeto.")
                    @Nullable UUID snapshotId,
            @Schema(nullable = true, description = "Ausente: abre uma conversa nova.")
                    @Nullable UUID conversationId,
            @Schema(nullable = true) @Nullable OptionsRequest options) {}

    record OptionsRequest(
            @Schema(
                            minimum = "1",
                            maximum = "" + AnswerService.MAX_EVIDENCE,
                            defaultValue = "" + AnswerService.DEFAULT_MAX_EVIDENCE,
                            nullable = true,
                            description = "Máximo de trechos enviados ao modelo como evidência.")
                    @Nullable Integer maxEvidence,
            @Schema(
                            allowableValues = {"LEXICAL", "VECTOR", "HYBRID"},
                            defaultValue = "HYBRID",
                            nullable = true,
                            description =
                                    "Busca usada para escolher as evidências. LEXICAL não usa o"
                                            + " provedor de embeddings.")
                    @Nullable String retrievalMode) {}

    record AnswerResponse(
            UUID answerId,
            UUID conversationId,
            UUID snapshotId,
            String question,
            @Schema(allowableValues = {"PENDING", "ANSWERED", "PARTIAL", "ABSTAINED", "FAILED"})
                    String status,
            @Schema(
                            nullable = true,
                            description =
                                    "Texto com marcadores [E1], [E2]… das evidências citadas;"
                                            + " null quando FAILED ou PENDING.")
                    @Nullable String answer,
            List<WarningResponse> warnings,
            @Schema(
                            description =
                                    "Citações descartadas por apontar evidência ou linhas não enviadas.")
                    int invalidCitationCount,
            @Schema(nullable = true, description = "Presente só quando FAILED.")
                    @Nullable ErrorResponse error,
            @Schema(description = "Todas as evidências enviadas ao modelo, citadas ou não.")
                    List<EvidenceResponse> evidence,
            @Schema(nullable = true, description = "null quando o modelo não foi consultado.")
                    @Nullable ModelResponse model,
            RetrievalResponse retrieval,
            MetricsResponse metrics,
            Instant createdAt,
            @Nullable Instant completedAt) {}

    record WarningResponse(
            @Schema(
                            allowableValues = {
                                "NO_EVIDENCE",
                                "INVALID_CITATIONS_REMOVED",
                                "NO_VALID_CITATIONS"
                            })
                    String code,
            String message) {}

    record ErrorResponse(String code, String message) {}

    record EvidenceResponse(
            UUID id,
            String citationLabel,
            @Schema(description = "Primeiro caminho em ordem lexicográfica com este conteúdo.")
                    String path,
            @Schema(description = "Demais caminhos do snapshot com o mesmo conteúdo.")
                    List<String> otherPaths,
            int startLine,
            int endLine,
            @Schema(nullable = true, description = "Trecho estreitado pela citação, se houver.")
                    @Nullable Integer citedStartLine,
            @Schema(nullable = true) @Nullable Integer citedEndLine,
            @Schema(description = "Cópia do trecho enviado ao modelo.") String snippet,
            @Schema(allowableValues = {"CITED", "NOT_CITED", "INVALID_RANGE"})
                    String validationStatus,
            @Schema(description = "Posição do trecho no resultado da busca, a partir de 1.")
                    int rank,
            RanksResponse ranks) {}

    @Schema(
            description =
                    "Posição do trecho em cada busca, a partir de 1; null quando ela não o trouxe.")
    record RanksResponse(
            @Schema(nullable = true) @Nullable Integer lexical,
            @Schema(nullable = true) @Nullable Integer vector,
            @Schema(nullable = true) @Nullable Integer path) {}

    record ModelResponse(String provider, String name) {}

    record RetrievalResponse(
            @Schema(allowableValues = {"LEXICAL", "VECTOR", "HYBRID"}) String mode,
            String promptVersion) {}

    @Schema(
            description =
                    "Tokens informados pelo provedor (null quando não informados). O custo é"
                            + " estimado pelo preço configurado, não é a fatura; UNKNOWN sem"
                            + " tokens ou sem preço.")
    record MetricsResponse(
            @Schema(nullable = true) @Nullable Long latencyMs,
            @Schema(nullable = true) @Nullable Long retrievalMs,
            @Schema(nullable = true) @Nullable Long generationMs,
            @Schema(nullable = true) @Nullable Integer inputTokens,
            @Schema(nullable = true) @Nullable Integer outputTokens,
            @Schema(nullable = true) @Nullable BigDecimal estimatedCost,
            @Schema(nullable = true) @Nullable String costCurrency,
            @Schema(allowableValues = {"ESTIMATED", "UNKNOWN"}) String costStatus) {}
}
