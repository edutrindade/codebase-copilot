package com.codebasecopilot.indexing;

import org.jspecify.annotations.Nullable;

// Aggregates of the index jobs of a project; durations of completed jobs only (D-084).
public record IndexUsage(
        long jobs,
        long completed,
        long failed,
        long cancelled,
        @Nullable Long averageDurationMs,
        @Nullable Long p95DurationMs) {}
