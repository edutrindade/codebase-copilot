package com.codebasecopilot.importjob.scan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// Version 0 and no updatedAt mean the project never saved settings.
record ProjectSettings(
        UUID projectId,
        List<String> excludePatterns,
        List<String> disabledDefaultRules,
        @Nullable Long maxFileBytes,
        @Nullable Long maxProjectBytes,
        int version,
        @Nullable Instant updatedAt) {

    static ProjectSettings defaults(UUID projectId) {
        return new ProjectSettings(projectId, List.of(), List.of(), null, null, 0, null);
    }
}
