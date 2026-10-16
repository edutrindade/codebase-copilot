package com.codebasecopilot.importjob;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Importação ZIP", description = "Envio e acompanhamento de arquivos ZIP de projetos.")
class ImportController {

    private static final Locale LOCALE = Locale.of("pt", "BR");

    private final ImportService service;
    private final MessageSource messages;

    ImportController(ImportService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @PostMapping(
            path = "/projects/{projectId}/imports/zip",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiErrors({
        ErrorCode.PROJECT_NOT_FOUND,
        ErrorCode.PROJECT_SOURCE_MISMATCH,
        ErrorCode.PROJECT_BUSY,
        ErrorCode.UPLOAD_TOO_LARGE
    })
    @Operation(operationId = "uploadZip", summary = "Envia um ZIP para importação")
    ResponseEntity<ImportResponse> uploadZip(
            @PathVariable UUID projectId,
            @RequestPart(name = "file", required = false) @Nullable MultipartFile file) {
        ImportResponse job = response(service.startZipImport(projectId, file));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/imports/" + job.id()))
                .body(job);
    }

    @GetMapping("/imports/{importJobId}")
    @ApiErrors(ErrorCode.IMPORT_NOT_FOUND)
    @Operation(operationId = "getImport", summary = "Busca uma importação")
    ImportResponse get(@PathVariable UUID importJobId) {
        return response(service.get(importJobId));
    }

    @GetMapping("/projects/{projectId}/imports")
    @ApiErrors(ErrorCode.PROJECT_NOT_FOUND)
    @Operation(operationId = "listImports", summary = "Lista as importações do projeto")
    PageResponse<ImportResponse> list(
            @PathVariable UUID projectId, @Valid @ParameterObject PageRequestParams params) {
        return service.list(projectId, params).map(this::response);
    }

    private ImportResponse response(ImportJob job) {
        ImportErrorResponse error =
                job.errorCode() == null
                        ? null
                        : new ImportErrorResponse(
                                job.errorCode().name(),
                                messages.getMessage(
                                        "import.error." + job.errorCode().name(), null, LOCALE));
        return new ImportResponse(
                job.id(),
                job.projectId(),
                job.status().name(),
                job.compressedBytes(),
                job.uncompressedBytes(),
                job.entryCount(),
                error,
                job.createdAt(),
                job.completedAt());
    }

    record ImportResponse(
            UUID id,
            UUID projectId,
            @Schema(allowableValues = {"PENDING", "EXTRACTING", "COMPLETED", "REJECTED", "FAILED"})
                    String status,
            long compressedBytes,
            @Nullable Long uncompressedBytes,
            @Nullable Integer entryCount,
            @Schema(nullable = true) @Nullable ImportErrorResponse error,
            Instant createdAt,
            @Nullable Instant completedAt) {}

    record ImportErrorResponse(String code, String message) {}
}
