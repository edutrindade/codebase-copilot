package com.codebasecopilot.history;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record AnswerMetrics(
        @Nullable Long latencyMs,
        @Nullable Integer inputTokens,
        @Nullable Integer outputTokens,
        @Nullable BigDecimal estimatedCost,
        @Nullable String costCurrency,
        CostStatus costStatus) {

    public static AnswerMetrics none() {
        return new AnswerMetrics(null, null, null, null, null, CostStatus.UNKNOWN);
    }
}
