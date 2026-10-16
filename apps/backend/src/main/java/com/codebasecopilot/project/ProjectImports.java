package com.codebasecopilot.project;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// Implemented by the importjob module so project never depends on it (D-031).
public interface ProjectImports {

    // Imports that no longer exist are absent from the result.
    Map<UUID, CurrentImport> find(Collection<UUID> importJobIds);

    // Called inside the deleting transaction; files are removed only after it commits.
    void onProjectDeleted(UUID projectId);

    record CurrentImport(UUID id, String status) {}
}
