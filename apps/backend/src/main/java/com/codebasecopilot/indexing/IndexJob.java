package com.codebasecopilot.indexing;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record IndexJob(
        UUID id,
        UUID projectId,
        UUID snapshotId,
        IndexJobStatus status,
        @Nullable IndexStage stage,
        @Nullable Integer progressTotal,
        @Nullable Integer progressCompleted,
        int attemptCount,
        @Nullable IndexErrorCode errorCode,
        Instant createdAt,
        @Nullable Instant startedAt,
        @Nullable Instant completedAt) {}
