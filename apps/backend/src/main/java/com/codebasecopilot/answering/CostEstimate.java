package com.codebasecopilot.answering;

import com.codebasecopilot.history.AnswerMetrics;
import com.codebasecopilot.history.CostStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

// ESTIMATED only with both token counts reported and both prices configured; anything else is
// UNKNOWN, never a guess (D-054).
final class CostEstimate {

    // The scale of answers.estimated_cost.
    static final int SCALE = 6;

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private CostEstimate() {}

    static AnswerMetrics metrics(
            long latencyMs,
            @Nullable Long inputTokens,
            @Nullable Long outputTokens,
            PricingProperties pricing) {
        Integer input = tokens(inputTokens);
        Integer output = tokens(outputTokens);
        BigDecimal inputPrice = pricing.inputPerMillion();
        BigDecimal outputPrice = pricing.outputPerMillion();
        if (input == null || output == null || inputPrice == null || outputPrice == null) {
            return new AnswerMetrics(latencyMs, input, output, null, null, CostStatus.UNKNOWN);
        }
        BigDecimal cost =
                inputPrice
                        .multiply(BigDecimal.valueOf(input))
                        .add(outputPrice.multiply(BigDecimal.valueOf(output)))
                        .divide(MILLION, SCALE, RoundingMode.HALF_UP);
        return new AnswerMetrics(
                latencyMs, input, output, cost, PricingProperties.CURRENCY, CostStatus.ESTIMATED);
    }

    private static @Nullable Integer tokens(@Nullable Long value) {
        return value == null ? null : (int) Math.min(value, Integer.MAX_VALUE);
    }
}
