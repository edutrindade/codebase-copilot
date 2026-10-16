package com.codebasecopilot.workspace;

import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
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
@RequestMapping("/api/v1/workspaces")
class WorkspaceController {

    private final WorkspaceService service;

    WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<WorkspaceResponse> create(@RequestBody CreateWorkspaceRequest request) {
        WorkspaceResponse workspace =
                WorkspaceResponse.of(new WorkspaceSummary(service.create(request.name()), 0));
        return ResponseEntity.created(URI.create("/api/v1/workspaces/" + workspace.id()))
                .body(workspace);
    }

    @GetMapping
    PageResponse<WorkspaceResponse> list(@Valid @ParameterObject PageRequestParams params) {
        return service.list(params).map(WorkspaceResponse::of);
    }

    @GetMapping("/{workspaceId}")
    @ApiErrors(ErrorCode.WORKSPACE_NOT_FOUND)
    WorkspaceResponse get(@PathVariable UUID workspaceId) {
        return WorkspaceResponse.of(service.get(workspaceId));
    }

    @DeleteMapping("/{workspaceId}")
    @ApiErrors({ErrorCode.WORKSPACE_NOT_FOUND, ErrorCode.WORKSPACE_NOT_EMPTY})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID workspaceId) {
        service.delete(workspaceId);
    }

    record CreateWorkspaceRequest(@Nullable String name) {}

    record WorkspaceResponse(UUID id, String name, long projectCount, Instant createdAt) {

        static WorkspaceResponse of(WorkspaceSummary summary) {
            Workspace workspace = summary.workspace();
            return new WorkspaceResponse(
                    workspace.id(),
                    workspace.name(),
                    summary.projectCount(),
                    workspace.createdAt());
        }
    }
}
