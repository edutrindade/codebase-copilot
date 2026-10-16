package com.codebasecopilot.indexing;

import com.codebasecopilot.importjob.scan.ScanResult;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Snapshots",
        description = "Resultados das indexações: contagens, mudanças, inventário e arquivos.")
class SnapshotController {

    private final SnapshotService service;
    private final JsonMapper json;

    SnapshotController(SnapshotService service, JsonMapper json) {
        this.service = service;
        this.json = json;
    }

    @GetMapping("/projects/{projectId}/snapshots")
    @ApiErrors(ErrorCode.PROJECT_NOT_FOUND)
    @Operation(operationId = "listSnapshots", summary = "Lista os snapshots do projeto")
    PageResponse<SnapshotResponse> list(
            @PathVariable UUID projectId, @Valid @ParameterObject PageRequestParams params) {
        return service.list(projectId, params).map(view -> response(view, false));
    }

    @GetMapping("/snapshots/{snapshotId}")
    @ApiErrors(ErrorCode.SNAPSHOT_NOT_FOUND)
    @Operation(
            operationId = "getSnapshot",
            summary = "Busca um snapshot, com o inventário da varredura")
    SnapshotResponse get(@PathVariable UUID snapshotId) {
        return response(service.get(snapshotId), true);
    }

    @GetMapping("/snapshots/{snapshotId}/files")
    @ApiErrors(ErrorCode.SNAPSHOT_NOT_FOUND)
    @Operation(operationId = "listSnapshotFiles", summary = "Lista os arquivos de um snapshot")
    PageResponse<SnapshotFileResponse> files(
            @PathVariable UUID snapshotId,
            @RequestParam(required = false) @Nullable FileStatus status,
            @RequestParam(required = false) @Nullable String pathPrefix,
            @Valid @ParameterObject PageRequestParams params) {
        return service.files(snapshotId, status, pathPrefix, params)
                .map(
                        f ->
                                new SnapshotFileResponse(
                                        f.relativePath(),
                                        f.status().name(),
                                        f.reason(),
                                        f.language(),
                                        f.sizeBytes(),
                                        f.lineCount(),
                                        f.contentHash(),
                                        f.chunkCount()));
    }

    private SnapshotResponse response(SnapshotService.SnapshotView view, boolean withInventory) {
        Snapshot s = view.snapshot();
        SnapshotRepository.Counts c = s.counts();
        SnapshotRepository.Changes ch = s.changes();
        SnapshotRepository.Embedding e = s.embedding();
        return new SnapshotResponse(
                s.id(),
                s.projectId(),
                s.status().name(),
                view.active(),
                s.settingsVersion(),
                s.chunkingVersion(),
                s.sourceImportJobId(),
                c == null
                        ? null
                        : new CountsResponse(
                                c.files(),
                                c.indexedFiles(),
                                c.unsupportedFiles(),
                                c.failedFiles(),
                                c.chunks(),
                                c.bytes()),
                ch == null
                        ? null
                        : new ChangesResponse(
                                ch.added(), ch.modified(), ch.removed(), ch.unchanged()),
                withInventory && s.inventory() != null
                        ? json.readValue(s.inventory(), ScanResult.class)
                        : null,
                e == null
                        ? null
                        : new EmbeddingResponse(
                                e.model().provider(),
                                e.model().model(),
                                e.model().dimensions(),
                                e.tokens()),
                s.lexicalVersion() == null ? null : new LexicalResponse(s.lexicalVersion()),
                s.createdAt(),
                s.completedAt(),
                s.prunedAt());
    }

    record SnapshotResponse(
            UUID id,
            UUID projectId,
            @Schema(allowableValues = {"BUILDING", "READY", "PARTIAL", "FAILED"}) String status,
            boolean active,
            int settingsVersion,
            String chunkingVersion,
            @Nullable UUID sourceImportId,
            @Schema(nullable = true) @Nullable CountsResponse counts,
            @Schema(nullable = true) @Nullable ChangesResponse changes,
            @Schema(
                            nullable = true,
                            description =
                                    "Aggregate of the scan, in the format of GET"
                                            + " /projects/{projectId}/inventory; null in lists")
                    @Nullable ScanResult inventory,
            @Schema(
                            nullable = true,
                            description =
                                    "Modelo dos embeddings do snapshot; null quando indexado sem"
                                            + " provedor de embeddings.")
                    @Nullable EmbeddingResponse embedding,
            @Schema(
                            nullable = true,
                            description =
                                    "Versão do índice lexical do snapshot; null quando indexado"
                                            + " antes da busca lexical.")
                    @Nullable LexicalResponse lexical,
            Instant createdAt,
            @Nullable Instant completedAt,
            @Nullable Instant prunedAt) {}

    record CountsResponse(
            int files,
            int indexedFiles,
            int unsupportedFiles,
            int failedFiles,
            int chunks,
            long bytes) {}

    record ChangesResponse(int added, int modified, int removed, int unchanged) {}

    record EmbeddingResponse(
            String provider,
            String model,
            int dimensions,
            @Schema(
                            nullable = true,
                            description =
                                    "Tokens informados pelo provedor; null se não informados.")
                    @Nullable Long tokens) {}

    record LexicalResponse(String version) {}

    record SnapshotFileResponse(
            String relativePath,
            @Schema(allowableValues = {"INDEXED", "UNSUPPORTED", "FAILED"}) String status,
            @Schema(
                            nullable = true,
                            allowableValues = {
                                "BINARY",
                                "INVALID_ENCODING",
                                "LINE_TOO_LONG",
                                "READ_ERROR",
                                "CHANGED_DURING_READ"
                            })
                    @Nullable String reason,
            String language,
            long sizeBytes,
            @Nullable Integer lineCount,
            @Nullable String contentHash,
            int chunkCount) {}
}
