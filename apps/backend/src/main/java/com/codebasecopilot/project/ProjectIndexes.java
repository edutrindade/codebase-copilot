package com.codebasecopilot.project;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// Implemented by the indexing module so project never depends on it (D-031). Statuses are
// strings because project cannot see the indexing enums.
public interface ProjectIndexes {

    // One batch lookup for a page of projects; projects without index data may be absent.
    Map<UUID, IndexState> find(Collection<Project> projects);

    record IndexState(
            @Nullable ActiveSnapshot activeSnapshot,
            @Nullable Indexing indexing,
            @Nullable LastJob lastJob,
            List<String> outdatedReasons) {

        public static final IndexState NONE = new IndexState(null, null, null, List.of());
    }

    record ActiveSnapshot(UUID id, String status, @Nullable Instant completedAt, Counts counts) {}

    record Counts(
            int files,
            int indexedFiles,
            int unsupportedFiles,
            int failedFiles,
            int chunks,
            long bytes) {}

    record Indexing(
            UUID id,
            String status,
            @Nullable String stage,
            @Nullable Integer total,
            @Nullable Integer completed,
            boolean cancelRequested) {}

    // The most recent index job that ended, successfully or not.
    record LastJob(UUID id, String status, Instant finishedAt, @Nullable String errorCode) {}
}
