package com.codebasecopilot.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VectorLiteralTest {

    @Test
    void formatsComponentsInPgvectorTextFormat() {
        assertThat(VectorLiteral.of(new float[] {1f, -0.5f, 0.25f})).isEqualTo("[1.0,-0.5,0.25]");
    }

    @Test
    void usesADotWhateverTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"));
            assertThat(String.format("%.1f", 0.5f)).isEqualTo("0,5");

            assertThat(VectorLiteral.of(new float[] {0.5f, 1.0E-5f})).isEqualTo("[0.5,1.0E-5]");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void rejectsNonFiniteComponents(float component) {
        assertThatThrownBy(() -> VectorLiteral.of(new float[] {0.1f, component}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("vector component 1 is not finite");
    }

    @Test
    void rejectsAnEmptyVector() {
        assertThatThrownBy(() -> VectorLiteral.of(new float[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
