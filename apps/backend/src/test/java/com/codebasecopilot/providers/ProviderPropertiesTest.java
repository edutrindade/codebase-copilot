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
class ProviderPropertiesTest {

    private static final String API_KEY = "sk-test-0123456789-must-not-leak";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ProvidersConfiguration.class)
                    .withBean(Clock.class, Clock::systemUTC);

    @Test
    void defaultsToNoProvider() {
        runner.run(
                context -> {
                    ProviderProperties properties = context.getBean(ProviderProperties.class);
                    assertThat(properties.provider()).isEqualTo(Provider.NONE);
                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.model()).isEqualTo("text-embedding-3-small");
                    assertThat(properties.dimensions()).isEqualTo(1536);
                    assertThat(properties.batchSize()).isEqualTo(64);
                    assertThat(properties.maxRetries()).isEqualTo(4);
                    assertThat(properties.maxRetryWait()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(properties.maxChunksPerJob()).isEqualTo(50_000);
                    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void startsWithFakeProviderWithoutKey() {
        runner.withPropertyValues("app.embeddings.provider=fake")
                .run(
                        context ->
                                assertThat(context.getBean(ProviderProperties.class).provider())
                                        .isEqualTo(Provider.FAKE));
    }

    @Test
    void startsWithOpenAiAndKey() {
        runner.withPropertyValues(
                        "app.embeddings.provider=openai", "app.embeddings.api-key=" + API_KEY)
                .run(
                        context ->
                                assertThat(context.getBean(ProviderProperties.class).provider())
                                        .isEqualTo(Provider.OPENAI));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsOpenAiWithoutKey(String key) {
        assertFailsWith(
                "app.embeddings.api-key is required when provider is openai",
                "app.embeddings.provider=openai",
                "app.embeddings.api-key=" + key);
    }

    @Test
    void rejectsOpenAiWithMissingKey() {
        assertFailsWith(
                "app.embeddings.api-key is required when provider is openai",
                "app.embeddings.provider=openai");
    }

    @Test
    void rejectsUnknownProvider() {
        runner.withPropertyValues("app.embeddings.provider=voyage")
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .hasStackTraceContaining(
                                                "Failed to bind properties under"
                                                        + " 'app.embeddings.provider'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "2001"})
    void rejectsDimensionsOutOfRange(String dimensions) {
        assertFailsWith(
                "app.embeddings.dimensions must be between 1 and 2000",
                "app.embeddings.dimensions=" + dimensions);
    }

    @Test
    void acceptsDimensionLimits() {
        runner.withPropertyValues("app.embeddings.dimensions=2000")
                .run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("app.embeddings.dimensions=1")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-5s"})
    void rejectsNonPositiveMaxRetryWait(String wait) {
        assertFailsWith(
                "app.embeddings.max-retry-wait must be positive",
                "app.embeddings.max-retry-wait=" + wait);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsNonPositiveMaxChunksPerJob(String limit) {
        assertFailsWith(
                "app.embeddings.max-chunks-per-job must be positive",
                "app.embeddings.max-chunks-per-job=" + limit);
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        assertFailsWith(
                "app.embeddings.batch-size must be positive", "app.embeddings.batch-size=0");
    }

    @Test
    void rejectsNegativeMaxRetries() {
        assertFailsWith(
                "app.embeddings.max-retries must not be negative", "app.embeddings.max-retries=-1");
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertFailsWith("app.embeddings.timeout must be positive", "app.embeddings.timeout=0s");
    }

    @Test
    void rejectsBlankModel() {
        assertFailsWith("app.embeddings.model must not be blank", "app.embeddings.model= ");
    }

    @Test
    void defaultsToOfficialBaseUrl() {
        runner.run(
                context ->
                        assertThat(context.getBean(ProviderProperties.class).baseUrl())
                                .isEqualTo(URI.create("https://api.openai.com/v1")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://api.openai.com/v1",
                "https://embeddings.example.com/v1",
                "http://localhost:8080/v1",
                "http://127.0.0.1:12345",
                "HTTP://LOCALHOST:8080"
            })
    void acceptsHttpsOrLoopbackHttp(String url) {
        runner.withPropertyValues("app.embeddings.base-url=" + url)
                .run(
                        context ->
                                assertThat(context.getBean(ProviderProperties.class).baseUrl())
                                        .isEqualTo(URI.create(url)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://api.openai.com/v1",
                "http://192.168.0.10:8080",
                "http://localhost.example.com",
                "http://127.0.0.2",
                "http://[::1]:8080"
            })
    void rejectsHttpOutsideLoopback(String url) {
        assertFailsWith(
                "app.embeddings.base-url must use https (http is allowed only for localhost or"
                        + " 127.0.0.1)",
                "app.embeddings.base-url=" + url);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"ftp://localhost/v1", "api.openai.com/v1", "file:///tmp/x", "https:/v1"})
    void rejectsNonHttpOrRelativeBaseUrl(String url) {
        assertFailsWith(
                "app.embeddings.base-url must be an absolute http(s) URL",
                "app.embeddings.base-url=" + url);
    }

    @Test
    void rejectsBaseUrlWithUserInfoWithoutEchoingIt() {
        runner.withPropertyValues("app.embeddings.base-url=https://user:s3cr3t@example.com/v1")
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .isInstanceOf(IllegalStateException.class)
                                        .hasMessageContaining(
                                                "app.embeddings.base-url must not contain user info")
                                        .hasMessageNotContaining("s3cr3t"));
    }

    @Test
    void startupFailureNeverContainsKey(CapturedOutput output) {
        runner.withPropertyValues(
                        "app.embeddings.provider=openai",
                        "app.embeddings.api-key=" + API_KEY,
                        "app.embeddings.dimensions=0")
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
                        "app.embeddings.provider=openai", "app.embeddings.api-key=" + API_KEY)
                .run(
                        context ->
                                assertThat(context.getBean(ProviderProperties.class).toString())
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
