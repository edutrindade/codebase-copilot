package com.codebasecopilot.answering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AnsweringPropertiesTest {

    @Test
    void defaults() {
        assertThat(bind(Map.of())).isEqualTo(new AnsweringProperties(20, 6000, 3));
    }

    @ParameterizedTest
    @CsvSource({
        "candidates, 0",
        "candidates, 51",
        "max-context-tokens, 0",
        "max-evidence-per-file, 0"
    })
    void rejectsOutOfRangeValues(String property, String value) {
        assertThatThrownBy(() -> bind(Map.of("app.answering." + property, value)))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageStartingWith("app.answering." + property);
    }

    private static AnsweringProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("app.answering", AnsweringProperties.class);
    }
}
