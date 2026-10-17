package com.codebasecopilot.answering;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.history.AnswerMetrics;
import com.codebasecopilot.history.CostStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CostEstimateTest {

    private static final PricingProperties LUNA =
            new PricingProperties(new BigDecimal("0.10"), new BigDecimal("0.50"));

    @Test
    void estimatesFromBothTokenCountsAndPrices() {
        AnswerMetrics metrics = CostEstimate.metrics(120, 6700L, 2000L, LUNA);

        // (6700 × 0.10 + 2000 × 0.50) / 1M = 0.00167
        assertThat(metrics.estimatedCost()).isEqualByComparingTo("0.00167");
        assertThat(metrics.estimatedCost().scale()).isEqualTo(CostEstimate.SCALE);
        assertThat(metrics.costCurrency()).isEqualTo("USD");
        assertThat(metrics.costStatus()).isEqualTo(CostStatus.ESTIMATED);
        assertThat(metrics.latencyMs()).isEqualTo(120);
        assertThat(metrics.inputTokens()).isEqualTo(6700);
        assertThat(metrics.outputTokens()).isEqualTo(2000);
    }

    @Test
    void roundsToTheStoredScale() {
        assertThat(CostEstimate.metrics(0, 1L, 1L, LUNA).estimatedCost())
                .isEqualByComparingTo("0.000001");
    }

    @Test
    void missingTokensAreUnknown() {
        assertUnknown(CostEstimate.metrics(5, null, 2000L, LUNA));
        assertUnknown(CostEstimate.metrics(5, 6700L, null, LUNA));
    }

    @Test
    void missingPriceIsUnknown() {
        assertUnknown(
                CostEstimate.metrics(
                        5, 6700L, 2000L, new PricingProperties(new BigDecimal("0.10"), null)));
        assertUnknown(CostEstimate.metrics(5, 6700L, 2000L, new PricingProperties(null, null)));
    }

    @Test
    void unknownCostKeepsTheReportedTokens() {
        AnswerMetrics metrics =
                CostEstimate.metrics(5, 6700L, 2000L, new PricingProperties(null, null));

        assertThat(metrics.inputTokens()).isEqualTo(6700);
        assertThat(metrics.outputTokens()).isEqualTo(2000);
    }

    private static void assertUnknown(AnswerMetrics metrics) {
        assertThat(metrics.costStatus()).isEqualTo(CostStatus.UNKNOWN);
        assertThat(metrics.estimatedCost()).isNull();
        assertThat(metrics.costCurrency()).isNull();
        assertThat(metrics.latencyMs()).isEqualTo(5);
    }
}
