package com.codebasecopilot.project;

import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Projetos",
        description = "Projetos de pasta local ou ZIP, com origem e estado da indexação.")
class ProjectController {

    private final ProjectService service;

    ProjectController(ProjectService service) {
        this.service = service;
    }

    @PostMapping("/workspaces/{workspaceId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiErrors({
        ErrorCode.WORKSPACE_NOT_FOUND,
        ErrorCode.LOCAL_ROOTS_NOT_CONFIGURED,
        ErrorCode.ROOT_NOT_FOUND,
        ErrorCode.PATH_OUTSIDE_ROOT,
        ErrorCode.PROJECT_ALREADY_EXISTS
    })
    @Operation(operationId = "createProject", summary = "Cria um projeto no workspace")
    ResponseEntity<ProjectResponse> create(
            @PathVariable UUID workspaceId, @RequestBody CreateProjectRequest request) {
        ProjectResponse project =
                ProjectResponse.of(
                        new ProjectSummary(
                                service.create(workspaceId, request.toCommand()),
                                null,
                                ProjectIndexes.IndexState.NONE));
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.id())).body(project);
    }

    @GetMapping("/workspaces/{workspaceId}/projects")
    @ApiErrors(ErrorCode.WORKSPACE_NOT_FOUND)
    @Operation(operationId = "listProjects", summary = "Lista os projetos do workspace")
    PageResponse<ProjectResponse> list(
            @PathVariable UUID workspaceId, @Valid @ParameterObject PageRequestParams params) {
        return service.list(workspaceId, params).map(ProjectResponse::of);
    }

    @GetMapping("/projects/{projectId}")
    @ApiErrors(ErrorCode.PROJECT_NOT_FOUND)
    @Operation(
            operationId = "getProject",
            summary = "Busca um projeto, com origem e estado da indexação")
    ProjectResponse get(@PathVariable UUID projectId) {
        return ProjectResponse.of(service.get(projectId));
    }

    @DeleteMapping("/projects/{projectId}")
    @ApiErrors({ErrorCode.PROJECT_NOT_FOUND, ErrorCode.PROJECT_BUSY})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteProject", summary = "Remove um projeto e todos os seus dados")
    void delete(@PathVariable UUID projectId) {
        service.delete(projectId);
    }

    record CreateProjectRequest(
            @Nullable String name,
            @Nullable SourceType sourceType,
            @Nullable String rootKey,
            @Nullable String relativePath) {

        CreateProjectCommand toCommand() {
            return new CreateProjectCommand(name, sourceType, rootKey, relativePath);
        }
    }

    record ProjectResponse(
            UUID id,
            UUID workspaceId,
            String name,
            SourceResponse source,
            @Schema(nullable = true) @Nullable ActiveSnapshotResponse activeSnapshot,
            @Schema(nullable = true) @Nullable IndexingResponse indexing,
            @ArraySchema(
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "SETTINGS_CHANGED",
                                                "SOURCE_REIMPORTED",
                                                "CHUNKING_CHANGED"
                                            }))
                    List<String> outdatedReasons,
            Instant createdAt) {

        static ProjectResponse of(ProjectSummary summary) {
            Project project = summary.project();
            ProjectImports.CurrentImport current = summary.currentImport();
            SourceResponse source =
                    switch (project.sourceType()) {
                        case LOCAL_FOLDER ->
                                new LocalFolderSourceResponse(
                                        project.sourceRootKey(), project.sourceRelativePath());
                        case ZIP ->
                                new ZipSourceResponse(
                                        current == null
                                                ? null
                                                : new CurrentImportResponse(
                                                        current.id(), current.status()));
                    };
            ProjectIndexes.IndexState index = summary.index();
            ProjectIndexes.ActiveSnapshot active = index.activeSnapshot();
            ProjectIndexes.Indexing indexing = index.indexing();
            return new ProjectResponse(
                    project.id(),
                    project.workspaceId(),
                    project.name(),
                    source,
                    active == null
                            ? null
                            : new ActiveSnapshotResponse(
                                    active.id(),
                                    active.status(),
                                    active.completedAt(),
                                    new SnapshotCountsResponse(
                                            active.counts().files(),
                                            active.counts().indexedFiles(),
                                            active.counts().unsupportedFiles(),
                                            active.counts().failedFiles(),
                                            active.counts().chunks(),
                                            active.counts().bytes())),
                    indexing == null
                            ? null
                            : new IndexingResponse(
                                    indexing.id(),
                                    indexing.status(),
                                    indexing.stage(),
                                    indexing.total() == null
                                            ? null
                                            : new ProgressResponse(
                                                    indexing.total(),
                                                    indexing.completed() == null
                                                            ? 0
                                                            : indexing.completed())),
                    index.outdatedReasons(),
                    project.createdAt());
        }
    }

    record ActiveSnapshotResponse(
            UUID id,
            @Schema(allowableValues = {"READY", "PARTIAL"}) String status,
            @Nullable Instant completedAt,
            SnapshotCountsResponse counts) {}

    record SnapshotCountsResponse(
            int files,
            int indexedFiles,
            int unsupportedFiles,
            int failedFiles,
            int chunks,
            long bytes) {}

    record IndexingResponse(
            UUID id,
            @Schema(allowableValues = {"PENDING", "RUNNING"}) String status,
            @Schema(
                            nullable = true,
                            allowableValues = {
                                "SCANNING",
                                "PROCESSING_FILES",
                                "EMBEDDING",
                                "FINALIZING"
                            })
                    @Nullable String stage,
            @Schema(nullable = true) @Nullable ProgressResponse progress) {}

    record ProgressResponse(int total, int completed) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = LocalFolderSourceResponse.class, name = "LOCAL_FOLDER"),
        @JsonSubTypes.Type(value = ZipSourceResponse.class, name = "ZIP")
    })
    @Schema(
            discriminatorProperty = "type",
            discriminatorMapping = {
                @DiscriminatorMapping(
                        value = "LOCAL_FOLDER",
                        schema = LocalFolderSourceResponse.class),
                @DiscriminatorMapping(value = "ZIP", schema = ZipSourceResponse.class)
            })
    sealed interface SourceResponse permits LocalFolderSourceResponse, ZipSourceResponse {}

    record LocalFolderSourceResponse(@Nullable String rootKey, @Nullable String relativePath)
            implements SourceResponse {}

    record ZipSourceResponse(@Schema(nullable = true) @Nullable CurrentImportResponse currentImport)
            implements SourceResponse {}

    record CurrentImportResponse(UUID id, String status) {}
}
