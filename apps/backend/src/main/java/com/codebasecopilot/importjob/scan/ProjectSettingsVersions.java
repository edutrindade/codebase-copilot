package com.codebasecopilot.importjob.scan;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

// Batch lookup of settings versions without depending on ProjectService, so the project module
// can reach it through its ports without a cycle. Projects without saved settings are version 0.
@Component
public class ProjectSettingsVersions {

    private final ProjectSettingsRepository repository;

    ProjectSettingsVersions(ProjectSettingsRepository repository) {
        this.repository = repository;
    }

    public Map<UUID, Integer> find(Collection<UUID> projectIds) {
        return repository.findVersions(projectIds);
    }
}
