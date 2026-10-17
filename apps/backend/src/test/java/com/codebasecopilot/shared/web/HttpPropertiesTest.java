package com.codebasecopilot.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import org.junit.jupiter.api.Test;

class HttpPropertiesTest {

    @Test
    void valuesAreCompareReady() {
        HttpProperties properties =
                new HttpProperties(List.of(" LocalHost "), List.of("HTTP://LOCALHOST:5173"));

        assertThat(properties.allowedHosts()).containsExactly("localhost");
        assertThat(properties.allowedOrigins()).containsExactly("http://localhost:5173");
    }

    @Test
    void rejectsValuesThatWouldNeverMatch() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new HttpProperties(List.of(), List.of()));
        assertThatIllegalStateException()
                .isThrownBy(() -> new HttpProperties(List.of("localhost"), List.of("http://x/")));
    }
}
