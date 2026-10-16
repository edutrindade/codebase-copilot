package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProvidersConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ProvidersConfiguration.class)
                    .withBean(Clock.class, Clock::systemUTC);

    @Test
    void noProviderBeanWithNone() {
        runner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(EmbeddingProvider.class);
                    assertThat(context).hasSingleBean(EmbeddingRetry.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"fake", "FAKE"})
    void fakeProvider(String provider) {
        runner.withPropertyValues(
                        "app.embeddings.provider=" + provider,
                        "app.embeddings.model=demo",
                        "app.embeddings.dimensions=8")
                .run(
                        context -> {
                            assertThat(context)
                                    .getBean(EmbeddingProvider.class)
                                    .isInstanceOf(FakeEmbeddingProvider.class);
                            assertThat(context.getBean(EmbeddingProvider.class).model())
                                    .isEqualTo(new ModelInfo("fake", "demo", 8));
                        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "OpenAI", "open-ai"})
    void openAiProvider(String provider) {
        runner.withPropertyValues(
                        "app.embeddings.provider=" + provider, "app.embeddings.api-key=sk-test")
                .run(
                        context ->
                                assertThat(context)
                                        .getBean(EmbeddingProvider.class)
                                        .isInstanceOf(OpenAiEmbeddingProvider.class));
    }

    @Test
    void openAiModelWithoutDocumentedLimitDoesNotStart() {
        runner.withPropertyValues(
                        "app.embeddings.provider=openai",
                        "app.embeddings.api-key=sk-test",
                        "app.embeddings.model=unknown-model")
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining("no known input limit"));
    }
}
