package com.codebasecopilot.answering;

import com.codebasecopilot.history.AnswerEvidence;
import com.codebasecopilot.history.AnswerMetrics;
import com.codebasecopilot.history.AnswerStatus;
import com.codebasecopilot.history.StoredAnswer;
import com.codebasecopilot.history.StoredEvidence;
import com.codebasecopilot.retrieval.Retrieval;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

// The answer as the API shows it, shared by the question, answer and conversation endpoints.
@Component
class AnswerResponses {

    private static final Locale LOCALE = Locale.of("pt", "BR");

    private final MessageSource messages;

    AnswerResponses(MessageSource messages) {
        this.messages = messages;
    }

    AnswerResponse answer(StoredAnswer answer) {
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
                answer.evidence().stream().map(AnswerResponses::evidence).toList(),
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

    static EvidenceDetailResponse evidenceDetail(
            StoredEvidence stored,
            boolean snapshotAvailable,
            Retrieval.ActiveVersion activeVersion) {
        AnswerEvidence evidence = stored.evidence();
        return new EvidenceDetailResponse(
                stored.id(),
                stored.answerId(),
                stored.snapshotId(),
                snapshotAvailable ? "AVAILABLE" : "PRUNED",
                evidence.citationLabel(),
                evidence.relativePath(),
                evidence.otherPaths(),
                evidence.contentHash(),
                evidence.startLine(),
                evidence.endLine(),
                evidence.citedStartLine(),
                evidence.citedEndLine(),
                evidence.snippet(),
                evidence.validation().name(),
                evidence.finalRank(),
                ranks(evidence),
                activeVersion.name());
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
                ranks(evidence));
    }

    private static RanksResponse ranks(AnswerEvidence evidence) {
        return new RanksResponse(
                evidence.rankLexical(), evidence.rankVector(), evidence.rankPath());
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
                    "Evidência como foi enviada ao modelo, copiada no momento da resposta;"
                            + " continua disponível depois da poda do snapshot.")
    record EvidenceDetailResponse(
            UUID id,
            UUID answerId,
            UUID snapshotId,
            @Schema(
                            allowableValues = {"AVAILABLE", "PRUNED"},
                            description =
                                    "PRUNED: snapshot removido; só a cópia do trecho está"
                                            + " disponível, não o arquivo inteiro.")
                    String snapshotStatus,
            String citationLabel,
            @Schema(description = "Primeiro caminho em ordem lexicográfica com este conteúdo.")
                    String path,
            @Schema(description = "Demais caminhos do snapshot com o mesmo conteúdo.")
                    List<String> otherPaths,
            String contentHash,
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
            RanksResponse ranks,
            @Schema(
                            allowableValues = {"SAME", "CHANGED", "REMOVED", "NO_ACTIVE_SNAPSHOT"},
                            description =
                                    "O arquivo no snapshot ativo do projeto: com o mesmo conteúdo,"
                                            + " alterado, ausente, ou sem snapshot ativo.")
                    String activeVersion) {}

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
