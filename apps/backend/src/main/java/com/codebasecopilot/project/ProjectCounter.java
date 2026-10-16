package com.codebasecopilot.project;

import com.codebasecopilot.workspace.WorkspaceProjectCounter;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProjectCounter implements WorkspaceProjectCounter {

    private final ProjectRepository repository;

    ProjectCounter(ProjectRepository repository) {
        this.repository = repository;
    }

    @Override
    public long countByWorkspace(UUID workspaceId) {
        return repository.countByWorkspace(workspaceId);
    }

    @Override
    public Map<UUID, Long> countByWorkspaces(Collection<UUID> workspaceIds) {
        return repository.countByWorkspaces(workspaceIds);
    }
}
