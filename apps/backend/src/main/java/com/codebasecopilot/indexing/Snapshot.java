package com.codebasecopilot.indexing;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// inventory is the JSON of the scan that built the snapshot (D-035, D-039).
record Snapshot(
        UUID id,
        UUID projectId,
        SnapshotStatus status,
        @Nullable UUID sourceImportJobId,
        int settingsVersion,
        String chunkingVersion,
        @Nullable String inventory,
        SnapshotRepository.@Nullable Counts counts,
        SnapshotRepository.@Nullable Changes changes,
        SnapshotRepository.@Nullable Embedding embedding,
        Instant createdAt,
        @Nullable Instant completedAt,
        @Nullable Instant prunedAt) {}
