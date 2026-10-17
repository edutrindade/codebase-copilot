package com.codebasecopilot.history;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

// Aggregates of the answers of a project; latencies of finished answers only (D-084).
public record AnswerUsage(
        long questions,
        long answered,
        long partial,
        long abstained,
        long failed,
        long pending,
        @Nullable Long p50LatencyMs,
        @Nullable Long p95LatencyMs,
        long inputTokens,
        long outputTokens,
        @Nullable BigDecimal estimatedCost,
        @Nullable String costCurrency,
        long unknownCostAnswers) {}
