package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.providers.ProviderProperties.Provider;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class GenerationPropertiesTest {

    private static final String API_KEY = "sk-test-0123456789-must-not-leak";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ProvidersConfiguration.class)
                    .withBean(Clock.class, Clock::systemUTC);

    @Test
    void defaultsToNoProvider() {
        runner.run(
                context -> {
                    GenerationProperties properties = context.getBean(GenerationProperties.class);
                    assertThat(properties.provider()).isEqualTo(Provider.NONE);
                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.model()).isEqualTo("gpt-6-luna");
                    assertThat(properties.reasoningEffort()).isEqualTo("low");
                    assertThat(properties.maxOutputTokens()).isEqualTo(2000);
                    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(properties.baseUrl())
                            .isEqualTo(URI.create("https://api.openai.com/v1"));
                });
    }

    @Test
    void startsWithFakeProviderWithoutKey() {
        runner.withPropertyValues("app.generation.provider=fake")
                .run(
                        context ->
                                assertThat(context.getBean(GenerationProperties.class).provider())
                                        .isEqualTo(Provider.FAKE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsOpenAiWithoutKey(String key) {
        assertFailsWith(
                "app.generation.api-key is required when provider is openai",
                "app.generation.provider=openai",
                "app.generation.api-key=" + key);
    }

    @Test
    void embeddingKeyDoesNotServeGeneration() {
        assertFailsWith(
                "app.generation.api-key is required when provider is openai",
                "app.generation.provider=openai",
                "app.embeddings.api-key=" + API_KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "minimal", "low", "medium", "high", "xhigh", "max", ""})
    void acceptsKnownReasoningEffortsOrBlank(String effort) {
        runner.withPropertyValues("app.generation.reasoning-effort=" + effort)
                .run(
                        context ->
                                assertThat(
                                                context.getBean(GenerationProperties.class)
                                                        .reasoningEffort())
                                        .isEqualTo(effort));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOW", "fast", "Medium"})
    void rejectsUnknownReasoningEffort(String effort) {
        assertFailsWith(
                "app.generation.reasoning-effort must be blank or one of",
                "app.generation.reasoning-effort=" + effort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsNonPositiveMaxOutputTokens(String tokens) {
        assertFailsWith(
                "app.generation.max-output-tokens must be positive",
                "app.generation.max-output-tokens=" + tokens);
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertFailsWith("app.generation.timeout must be positive", "app.generation.timeout=0s");
    }

    @Test
    void rejectsBlankModel() {
        assertFailsWith("app.generation.model must not be blank", "app.generation.model= ");
    }

    @Test
    void rejectsHttpOutsideLoopback() {
        assertFailsWith(
                "app.generation.base-url must use https",
                "app.generation.base-url=http://api.openai.com/v1");
    }

    @Test
    void rejectsBaseUrlWithUserInfoWithoutEchoingIt() {
        runner.withPropertyValues("app.generation.base-url=https://user:s3cr3t@example.com/v1")
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining(
                                                "app.generation.base-url must not contain user info")
                                        .hasMessageNotContaining("s3cr3t"));
    }

    @Test
    void startupFailureNeverContainsKey(CapturedOutput output) {
        runner.withPropertyValues(
                        "app.generation.provider=openai",
                        "app.generation.api-key=" + API_KEY,
                        "app.generation.max-output-tokens=0")
                .run(
                        context -> {
                            Throwable failure = context.getStartupFailure();
                            assertThat(failure).isNotNull();
                            for (Throwable t = failure; t != null; t = t.getCause()) {
                                assertThat(String.valueOf(t.getMessage())).doesNotContain(API_KEY);
                            }
                        });

        assertThat(output.getAll()).doesNotContain(API_KEY);
    }

    @Test
    void toStringRedactsKey() {
        runner.withPropertyValues(
                        "app.generation.provider=openai", "app.generation.api-key=" + API_KEY)
                .run(
                        context ->
                                assertThat(context.getBean(GenerationProperties.class).toString())
                                        .doesNotContain(API_KEY)
                                        .contains("apiKey=<redacted>"));
    }

    private void assertFailsWith(String message, String... properties) {
        runner.withPropertyValues(properties)
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .isInstanceOf(IllegalStateException.class)
                                        .hasMessageContaining(message));
    }
}
