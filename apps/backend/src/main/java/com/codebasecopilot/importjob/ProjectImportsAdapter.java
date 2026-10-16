package com.codebasecopilot.importjob;

import com.codebasecopilot.project.ProjectImports;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class ProjectImportsAdapter implements ProjectImports {

    private final ImportJobRepository repository;
    private final ImportFiles files;

    ProjectImportsAdapter(ImportJobRepository repository, ImportFiles files) {
        this.repository = repository;
        this.files = files;
    }

    @Override
    public Map<UUID, CurrentImport> find(Collection<UUID> importJobIds) {
        return repository.findStatuses(importJobIds).entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e -> new CurrentImport(e.getKey(), e.getValue().name())));
    }

    @Override
    public void onProjectDeleted(UUID projectId) {
        files.deleteAfterCommit(repository.findIdsByProject(projectId));
    }
}
