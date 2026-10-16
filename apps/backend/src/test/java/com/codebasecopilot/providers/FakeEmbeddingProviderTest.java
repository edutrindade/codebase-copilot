package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FakeEmbeddingProviderTest {

    private final FakeEmbeddingProvider provider = new FakeEmbeddingProvider("fake-model", 256);

    @Test
    void sameTextGivesSameVector() {
        FakeEmbeddingProvider other = new FakeEmbeddingProvider("fake-model", 256);

        assertThat(vector("parse the config file"))
                .containsExactly(other.embed(List.of("parse the config file")).vectors().get(0));
    }

    @Test
    void ignoresCaseAndPunctuation() {
        assertThat(vector("Parse(config);")).containsExactly(vector("parse config"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8, 1536, 2000})
    void hasConfiguredDimensions(int dimensions) {
        FakeEmbeddingProvider sized = new FakeEmbeddingProvider("fake-model", dimensions);

        assertThat(sized.embed(List.of("some text")).vectors().get(0)).hasSize(dimensions);
        assertThat(sized.model()).isEqualTo(new ModelInfo("fake", "fake-model", dimensions));
    }

    @ParameterizedTest
    @ValueSource(strings = {"one", "repeated repeated words words", "", "   {}  ", "áçãõ 42"})
    void vectorsHaveUnitNorm(String text) {
        double sum = 0;
        for (float component : vector(text)) {
            sum += component * component;
        }

        assertThat(Math.sqrt(sum)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void textsSharingTermsAreCloser() {
        float[] query = vector("load user profile from database");
        float[] related = vector("user profile repository reads the database");
        float[] unrelated = vector("render chart colors in canvas");

        assertThat(cosine(query, related)).isGreaterThan(cosine(query, unrelated));
    }

    @Test
    void keepsInputOrderAndCountsTerms() {
        EmbeddingResult result = provider.embed(List.of("alpha beta", "gamma", "alpha beta"));

        assertThat(result.vectors()).hasSize(3);
        assertThat(result.vectors().get(0)).containsExactly(result.vectors().get(2));
        assertThat(result.vectors().get(0)).isNotEqualTo(result.vectors().get(1));
        assertThat(result.tokens()).isEqualTo(5L);
    }

    @Test
    void emptyBatchGivesNoVectors() {
        EmbeddingResult result = provider.embed(List.of());

        assertThat(result.vectors()).isEmpty();
        assertThat(result.tokens()).isZero();
    }

    private float[] vector(String text) {
        return provider.embed(List.of(text)).vectors().get(0);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}
