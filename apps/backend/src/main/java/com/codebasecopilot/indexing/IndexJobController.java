package com.codebasecopilot.indexing;

import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Indexação", description = "Jobs que criam snapshots a partir da origem do projeto.")
class IndexJobController {

    private static final Locale LOCALE = Locale.of("pt", "BR");

    private final IndexJobService service;
    private final MessageSource messages;

    IndexJobController(IndexJobService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @PostMapping("/projects/{projectId}/index-jobs")
    @ApiErrors({
        ErrorCode.PROJECT_NOT_FOUND,
        ErrorCode.SOURCE_ROOT_NOT_CONFIGURED,
        ErrorCode.SOURCE_DIRECTORY_NOT_FOUND,
        ErrorCode.SOURCE_IS_SYMLINK,
        ErrorCode.SOURCE_NOT_READABLE,
        ErrorCode.SOURCE_NOT_IMPORTED,
        ErrorCode.PROJECT_BUSY
    })
    @Operation(operationId = "createIndexJob", summary = "Inicia a indexação do projeto")
    ResponseEntity<IndexJobResponse> create(@PathVariable UUID projectId) {
        IndexJobResponse job = response(service.create(projectId));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/index-jobs/" + job.id()))
                .body(job);
    }

    @GetMapping("/index-jobs/{jobId}")
    @ApiErrors(ErrorCode.INDEX_JOB_NOT_FOUND)
    @Operation(operationId = "getIndexJob", summary = "Busca um job de indexação")
    IndexJobResponse get(@PathVariable UUID jobId) {
        return response(service.get(jobId));
    }

    @PostMapping("/index-jobs/{jobId}/cancel")
    @ApiErrors({ErrorCode.INDEX_JOB_NOT_FOUND, ErrorCode.JOB_NOT_ACTIVE})
    @Operation(
            operationId = "cancelIndexJob",
            summary = "Cancela um job de indexação",
            description =
                    "Um job PENDING termina na hora como CANCELLED; num job RUNNING o pedido fica"
                            + " registrado (cancelRequested) e o job para na próxima etapa ou lote."
                            + " O snapshot ativo não muda.")
    ResponseEntity<IndexJobResponse> cancel(@PathVariable UUID jobId) {
        return ResponseEntity.accepted().body(response(service.cancel(jobId)));
    }

    @GetMapping("/projects/{projectId}/index-jobs")
    @ApiErrors(ErrorCode.PROJECT_NOT_FOUND)
    @Operation(operationId = "listIndexJobs", summary = "Lista os jobs de indexação do projeto")
    PageResponse<IndexJobResponse> list(
            @PathVariable UUID projectId, @Valid @ParameterObject PageRequestParams params) {
        return service.list(projectId, params).map(this::response);
    }

    private IndexJobResponse response(IndexJob job) {
        IndexJobErrorResponse error =
                job.errorCode() == null
                        ? null
                        : new IndexJobErrorResponse(
                                job.errorCode().name(),
                                messages.getMessage(
                                        "index.error." + job.errorCode().name(), null, LOCALE));
        ProgressResponse progress =
                job.progressTotal() == null
                        ? null
                        : new ProgressResponse(
                                job.progressTotal(),
                                job.progressCompleted() == null ? 0 : job.progressCompleted());
        return new IndexJobResponse(
                job.id(),
                job.projectId(),
                job.snapshotId(),
                job.status().name(),
                job.stage() == null ? null : job.stage().name(),
                progress,
                job.attemptCount(),
                error,
                job.cancelRequested(),
                job.createdAt(),
                job.startedAt(),
                job.completedAt());
    }

    record IndexJobResponse(
            UUID id,
            UUID projectId,
            UUID snapshotId,
            @Schema(allowableValues = {"PENDING", "RUNNING", "COMPLETED", "FAILED", "CANCELLED"})
                    String status,
            @Schema(
                            nullable = true,
                            allowableValues = {
                                "SCANNING",
                                "PROCESSING_FILES",
                                "LEXICAL",
                                "EMBEDDING",
                                "FINALIZING"
                            })
                    @Nullable String stage,
            @Schema(nullable = true) @Nullable ProgressResponse progress,
            int attempts,
            @Schema(nullable = true) @Nullable IndexJobErrorResponse error,
            @Schema(description = "Cancelamento pedido e ainda não concluído, ou concluído.")
                    boolean cancelRequested,
            Instant createdAt,
            @Nullable Instant startedAt,
            @Nullable Instant completedAt) {}

    record ProgressResponse(int total, int completed) {}

    record IndexJobErrorResponse(String code, String message) {}
}
