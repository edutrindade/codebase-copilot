package com.codebasecopilot.answering;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

// USD per million tokens of the generation model (P9 of Etapa 6). No default: a price is
// configuration copied from the provider's page, not a bill, and without it the cost is unknown.
@ConfigurationProperties("app.generation.pricing")
record PricingProperties(
        @Nullable BigDecimal inputPerMillion, @Nullable BigDecimal outputPerMillion) {

    static final String CURRENCY = "USD";

    PricingProperties {
        if (negative(inputPerMillion) || negative(outputPerMillion)) {
            throw new IllegalStateException("app.generation.pricing must not be negative");
        }
    }

    boolean configured() {
        return inputPerMillion != null && outputPerMillion != null;
    }

    private static boolean negative(@Nullable BigDecimal value) {
        return value != null && value.signum() < 0;
    }
}
