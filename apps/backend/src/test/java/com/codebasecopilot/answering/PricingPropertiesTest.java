package com.codebasecopilot.answering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class PricingPropertiesTest {

    @Test
    void hasNoDefaultPrice() {
        PricingProperties pricing = bind(Map.of());

        assertThat(pricing.inputPerMillion()).isNull();
        assertThat(pricing.outputPerMillion()).isNull();
        assertThat(pricing.configured()).isFalse();
    }

    // application.yml maps unset environment variables to empty values.
    @Test
    void emptyValuesAreNoPrice() {
        PricingProperties pricing =
                bind(
                        Map.of(
                                "app.generation.pricing.input-per-million", "",
                                "app.generation.pricing.output-per-million", ""));

        assertThat(pricing.configured()).isFalse();
    }

    @Test
    void bindsBothPrices() {
        PricingProperties pricing =
                bind(
                        Map.of(
                                "app.generation.pricing.input-per-million", "0.10",
                                "app.generation.pricing.output-per-million", "0.50"));

        assertThat(pricing.inputPerMillion()).isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(pricing.outputPerMillion()).isEqualByComparingTo(new BigDecimal("0.50"));
        assertThat(pricing.configured()).isTrue();
    }

    @Test
    void rejectsNegativePrices() {
        assertThatThrownBy(() -> bind(Map.of("app.generation.pricing.output-per-million", "-0.5")))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageStartingWith("app.generation.pricing");
    }

    private static PricingProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("app.generation.pricing", PricingProperties.class);
    }
}
