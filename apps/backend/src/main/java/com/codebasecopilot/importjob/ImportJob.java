package com.codebasecopilot.importjob;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record ImportJob(
        UUID id,
        UUID projectId,
        ImportStatus status,
        long compressedBytes,
        @Nullable Long uncompressedBytes,
        @Nullable Integer entryCount,
        @Nullable ImportErrorCode errorCode,
        Instant createdAt,
        @Nullable Instant completedAt) {}
