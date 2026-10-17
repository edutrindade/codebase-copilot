package com.codebasecopilot.answering;

import com.codebasecopilot.history.AnswerHistory;
import com.codebasecopilot.history.AnswerUsage;
import com.codebasecopilot.indexing.IndexUsage;
import com.codebasecopilot.indexing.IndexingUsage;
import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Computed from index_jobs and answers on every request; no event table (D-084).
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Uso", description = "Indexações, perguntas, tokens e custo estimado do projeto.")
class UsageController {

    private final ProjectService projects;
    private final IndexingUsage indexing;
    private final AnswerHistory history;

    UsageController(ProjectService projects, IndexingUsage indexing, AnswerHistory history) {
        this.projects = projects;
        this.indexing = indexing;
        this.history = history;
    }

    @GetMapping("/projects/{projectId}/usage")
    @ApiErrors(ErrorCode.PROJECT_NOT_FOUND)
    @Operation(
            operationId = "getProjectUsage",
            summary = "Uso agregado do projeto",
            description =
                    "Calculado sobre os jobs de indexação e as respostas guardadas; conversas"
                            + " removidas saem da conta.")
    UsageResponse get(@PathVariable UUID projectId) {
        projects.find(projectId);
        IndexUsage index = indexing.find(projectId);
        AnswerUsage answers = history.usage(projectId);
        return new UsageResponse(
                new IndexingUsageResponse(
                        index.jobs(),
                        index.completed(),
                        index.failed(),
                        index.cancelled(),
                        index.averageDurationMs(),
                        index.p95DurationMs()),
                new QuestionUsageResponse(
                        answers.questions(),
                        answers.answered(),
                        answers.partial(),
                        answers.abstained(),
                        answers.failed(),
                        answers.pending(),
                        answers.p50LatencyMs(),
                        answers.p95LatencyMs()),
                new CostUsageResponse(
                        answers.inputTokens(),
                        answers.outputTokens(),
                        answers.estimatedCost(),
                        answers.costCurrency(),
                        answers.unknownCostAnswers()));
    }

    record UsageResponse(
            IndexingUsageResponse indexing,
            QuestionUsageResponse questions,
            CostUsageResponse cost) {}

    record IndexingUsageResponse(
            @Schema(description = "Todos os jobs, inclusive os em andamento.") long total,
            long completed,
            long failed,
            long cancelled,
            @Schema(nullable = true, description = "Dos jobs COMPLETED.")
                    @Nullable Long averageDurationMs,
            @Schema(nullable = true, description = "Dos jobs COMPLETED.")
                    @Nullable Long p95DurationMs) {}

    record QuestionUsageResponse(
            long total,
            long answered,
            long partial,
            long abstained,
            long failed,
            long pending,
            @Schema(nullable = true, description = "Das respostas terminadas.")
                    @Nullable Long p50LatencyMs,
            @Schema(nullable = true, description = "Das respostas terminadas.")
                    @Nullable Long p95LatencyMs) {}

    record CostUsageResponse(
            long inputTokens,
            long outputTokens,
            @Schema(
                            nullable = true,
                            description =
                                    "Soma das respostas com custo ESTIMATED; null se nenhuma"
                                            + " tem.")
                    @Nullable BigDecimal estimatedCost,
            @Schema(nullable = true) @Nullable String currency,
            @Schema(description = "Respostas que chamaram o modelo e ficaram com custo UNKNOWN.")
                    long unknownCostAnswers) {}
}
