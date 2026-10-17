package com.codebasecopilot.providers;

import static com.codebasecopilot.providers.StubOpenAiServer.component;
import static com.codebasecopilot.providers.StubOpenAiServer.vectors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.codebasecopilot.providers.EmbeddingFailure.Type;
import com.codebasecopilot.providers.ProviderProperties.Provider;
import com.codebasecopilot.providers.StubOpenAiServer.Reply;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class OpenAiEmbeddingProviderTest {

    private static final String API_KEY = "sk-test-0123456789-must-not-leak";
    private static final String CHUNK = "class PaymentService { secretChunkContent(); }";
    private static final String PROVIDER_DETAIL = "provider-detail-must-not-leak";
    private static final String USAGE = "{\"prompt_tokens\":12,\"total_tokens\":12}";
    private static final int DIMENSIONS = 4;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);

    private StubOpenAiServer server;
    private OpenAiEmbeddingProvider provider;

    @BeforeEach
    void start() throws IOException {
        server = new StubOpenAiServer();
        provider = provider(server.baseUrl(), Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        provider.close();
        server.close();
    }

    @Test
    void returnsOneVectorPerTextInInputOrder() {
        server.reply(Reply.json(200, vectors(DIMENSIONS, USAGE, 2, 0, 1)));

        EmbeddingResult result = provider.embed(List.of("a", "b", "c"));

        assertThat(result.vectors()).hasSize(3);
        for (int index = 0; index < 3; index++) {
            assertThat(result.vectors().get(index))
                    .hasSize(DIMENSIONS)
                    .startsWith(component(index, 0), component(index, 1));
        }
    }

    @Test
    void sendsModelDimensionsTextsAndKey() {
        server.reply(Reply.json(200, vectors(DIMENSIONS, USAGE, 0, 1)));

        provider.embed(List.of("first", "second"));

        assertThat(server.requests())
                .singleElement()
                .satisfies(
                        request -> {
                            assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
                            assertThat(request.body())
                                    .contains("\"input\":[\"first\",\"second\"]")
                                    .contains("\"model\":\"text-embedding-3-small\"")
                                    .contains("\"dimensions\":" + DIMENSIONS)
                                    .contains("\"encoding_format\":\"float\"");
                        });
    }

    @Test
    void reportsModel() {
        assertThat(provider.model())
                .isEqualTo(new ModelInfo("openai", "text-embedding-3-small", DIMENSIONS));
    }

    @Test
    void passesReportedTokenUsage() {
        server.reply(Reply.json(200, vectors(DIMENSIONS, USAGE, 0)));

        assertThat(provider.embed(List.of("a")).tokens()).isEqualTo(12L);
    }

    @Test
    void fallsBackToPromptTokens() {
        server.reply(Reply.json(200, vectors(DIMENSIONS, "{\"prompt_tokens\":7}", 0)));

        assertThat(provider.embed(List.of("a")).tokens()).isEqualTo(7L);
    }

    @Test
    void tokensAreNullWhenUsageIsNotReported() {
        server.reply(Reply.json(200, vectors(DIMENSIONS, "", 0)));

        EmbeddingResult result = provider.embed(List.of("a"));

        assertThat(result.vectors()).hasSize(1);
        assertThat(result.tokens()).isNull();
    }

    @Test
    void emptyBatchSendsNoRequest() {
        EmbeddingResult result = provider.embed(List.of());

        assertThat(result.vectors()).isEmpty();
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void rejectsVectorWithOtherDimensions() {
        server.reply(Reply.json(200, vectors(DIMENSIONS + 1, USAGE, 0)));

        assertFailure(() -> provider.embed(List.of("a")), Type.INVALID_REQUEST, null)
                .hasMessageContaining("5 dimensions, expected 4");
    }

    @Test
    void rejectsFewerVectorsThanTexts() {
        server.reply(Reply.json(200, vectors(DIMENSIONS, USAGE, 0)));

        assertFailure(() -> provider.embed(List.of("a", "b")), Type.INVALID_REQUEST, null)
                .hasMessageContaining("1 vectors for 2 texts");
    }

    @Test
    void rejectsMoreVectorsThanTexts() {
        server.reply(Reply.json(200, vectors(DIMENSIONS, USAGE, 0, 1)));

        assertFailure(() -> provider.embed(List.of("a")), Type.INVALID_REQUEST, null);
    }

    @Test
    void rejectsRepeatedIndex() {
        server.reply(Reply.json(200, vectors(DIMENSIONS, USAGE, 0, 0)));

        assertFailure(() -> provider.embed(List.of("a", "b")), Type.INVALID_REQUEST, null)
                .hasMessageContaining("invalid or repeated vector index");
    }

    @Test
    void rejectsIndexOutOfRange() {
        server.reply(Reply.json(200, vectors(DIMENSIONS, USAGE, 0, 2)));

        assertFailure(() -> provider.embed(List.of("a", "b")), Type.INVALID_REQUEST, null);
    }

    @Test
    void rejectsUnreadableResponse() {
        server.reply(Reply.json(200, "{\"data\":\"" + PROVIDER_DETAIL + "\"}"));

        assertFailure(() -> provider.embed(List.of("a")), Type.INVALID_REQUEST, null);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void credentialErrorsAreUnauthorized(int status) {
        server.reply(Reply.json(status, error()));

        assertFailure(() -> provider.embed(List.of("a")), Type.UNAUTHORIZED, status);
    }

    @Test
    void rateLimitCarriesRetryAfterSeconds() {
        server.reply(Reply.json(429, error()).withHeader("Retry-After", "7"));

        assertFailure(() -> provider.embed(List.of("a")), Type.RATE_LIMITED, 429)
                .satisfies(e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(7)));
    }

    @Test
    void rateLimitCarriesRetryAfterDate() {
        server.reply(
                Reply.json(429, error())
                        .withHeader("Retry-After", "Fri, 16 Oct 2026 12:00:30 GMT"));

        assertFailure(() -> provider.embed(List.of("a")), Type.RATE_LIMITED, 429)
                .satisfies(e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void rateLimitCarriesRetryAfterMillis() {
        server.reply(Reply.json(429, error()).withHeader("retry-after-ms", "1500"));

        assertFailure(() -> provider.embed(List.of("a")), Type.RATE_LIMITED, 429)
                .satisfies(e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofMillis(1500)));
    }

    @Test
    void rateLimitWithoutRetryAfter() {
        server.reply(Reply.json(429, error()));

        assertFailure(() -> provider.embed(List.of("a")), Type.RATE_LIMITED, 429)
                .satisfies(e -> assertThat(e.retryAfter()).isNull());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 408})
    void serverErrorsAreUnavailable(int status) {
        server.reply(Reply.json(status, error()));

        assertFailure(() -> provider.embed(List.of("a")), Type.UNAVAILABLE, status);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 422})
    void requestErrorsAreInvalidRequest(int status) {
        server.reply(Reply.json(status, error()));

        assertFailure(() -> provider.embed(List.of("a")), Type.INVALID_REQUEST, status);
    }

    @Test
    void timeoutIsUnavailable() {
        OpenAiEmbeddingProvider impatient = provider(server.baseUrl(), Duration.ofMillis(200));
        server.reply(
                Reply.json(200, vectors(DIMENSIONS, USAGE, 0)).delayedBy(Duration.ofSeconds(10)));

        try (impatient) {
            long start = System.nanoTime();
            assertFailure(() -> impatient.embed(List.of("a")), Type.UNAVAILABLE, null);
            assertThat(Duration.ofNanos(System.nanoTime() - start))
                    .isLessThan(Duration.ofSeconds(5));
        }
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void connectionRefusedIsUnavailable() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        try (OpenAiEmbeddingProvider unreachable =
                provider(URI.create("http://127.0.0.1:" + port + "/v1"), Duration.ofSeconds(2))) {
            assertFailure(() -> unreachable.embed(List.of("a")), Type.UNAVAILABLE, null);
        }
    }

    // Billing 429s per the OpenAI error codes guide (consulted on 16/10/2026).
    @ParameterizedTest
    @ValueSource(
            strings = {
                "credit_balance_exhausted",
                "organization_spend_limit_exceeded",
                "project_spend_limit_exceeded",
                "organization_usage_limit_exceeded"
            })
    void quotaCodesAreQuotaExceeded(String code) {
        server.reply(Reply.json(429, error("insufficient_quota", code)));

        assertFailure(() -> provider.embed(List.of("a")), Type.QUOTA_EXCEEDED, 429);
    }

    @Test
    void insufficientQuotaTypeWithoutCodeIsQuotaExceeded() {
        server.reply(Reply.json(429, error("insufficient_quota", null)));

        assertFailure(() -> provider.embed(List.of("a")), Type.QUOTA_EXCEEDED, 429);
    }

    @Test
    void slowDownIsRateLimited() {
        server.reply(Reply.json(429, error("rate_limit_error", "slow_down")));

        assertFailure(() -> provider.embed(List.of("a")), Type.RATE_LIMITED, 429);
    }

    @Test
    void quotaExceededReachesTheServerOnceAndIgnoresRetryAfter() {
        server.reply(
                Reply.json(429, error("insufficient_quota", "credit_balance_exhausted"))
                        .withHeader("Retry-After", "1"));
        List<Duration> waits = new ArrayList<>();
        EmbeddingRetry retry =
                new EmbeddingRetry(4, Duration.ofSeconds(1), Duration.ofSeconds(60), waits::add);

        assertThatThrownBy(() -> retry.call(() -> provider.embed(List.of("a"))))
                .isInstanceOfSatisfying(
                        EmbeddingFailure.class,
                        e -> {
                            assertThat(e.type()).isEqualTo(Type.QUOTA_EXCEEDED);
                            assertThat(e.retryable()).isFalse();
                            assertThat(e.retryAfter()).isNull();
                        });

        assertThat(server.requests()).hasSize(1);
        assertThat(waits).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "slow_down"})
    void rateLimitIsRetriedOnlyByEmbeddingRetry(String code) {
        String body = code.equals("none") ? error() : error("rate_limit_error", code);
        server.reply(Reply.json(429, body).withHeader("Retry-After", "1"));
        List<Duration> waits = new ArrayList<>();
        EmbeddingRetry retry =
                new EmbeddingRetry(4, Duration.ofSeconds(1), Duration.ofSeconds(60), waits::add);

        assertThatThrownBy(() -> retry.call(() -> provider.embed(List.of("a"))))
                .isInstanceOfSatisfying(
                        EmbeddingFailure.class,
                        e -> assertThat(e.type()).isEqualTo(Type.RATE_LIMITED));

        assertThat(server.requests()).hasSize(5);
        assertThat(waits).hasSize(4);
    }

    @Test
    void unauthorizedIsNotRetried() {
        server.reply(Reply.json(401, error()));
        EmbeddingRetry retry =
                new EmbeddingRetry(4, Duration.ofSeconds(1), Duration.ofSeconds(60), wait -> {});

        assertThatThrownBy(() -> retry.call(() -> provider.embed(List.of("a"))))
                .isInstanceOf(EmbeddingFailure.class);

        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void failuresNeverExposeTextsKeyOrProviderBody(CapturedOutput output) {
        EmbeddingRetry retry =
                new EmbeddingRetry(2, Duration.ofSeconds(1), Duration.ofSeconds(60), wait -> {});
        List<Reply> replies =
                List.of(
                        Reply.json(400, error()),
                        Reply.json(401, error()),
                        Reply.json(429, error()),
                        Reply.json(429, error("insufficient_quota", "credit_balance_exhausted")),
                        Reply.json(500, error()),
                        Reply.json(200, "{\"data\":[{\"index\":\"" + PROVIDER_DETAIL + "\"}]}"),
                        Reply.json(200, vectors(DIMENSIONS + 1, USAGE, 0)));

        for (Reply reply : replies) {
            server.reply(reply);
            assertThatThrownBy(() -> retry.call(() -> provider.embed(List.of(CHUNK))))
                    .isInstanceOfSatisfying(
                            EmbeddingFailure.class,
                            e -> {
                                for (Throwable t = e; t != null; t = t.getCause()) {
                                    assertThat(String.valueOf(t.getMessage()))
                                            .doesNotContain(
                                                    CHUNK,
                                                    API_KEY,
                                                    PROVIDER_DETAIL,
                                                    "credit_balance_exhausted",
                                                    "insufficient_quota");
                                }
                            });
        }

        assertThat(output.getAll())
                .doesNotContain(
                        CHUNK,
                        API_KEY,
                        PROVIDER_DETAIL,
                        "credit_balance_exhausted",
                        "insufficient_quota");
    }

    private static String error() {
        return error("error", null);
    }

    private static String error(String type, @Nullable String code) {
        return "{\"error\":{\"message\":\"%s %s\",\"type\":\"%s\"%s}}"
                .formatted(
                        PROVIDER_DETAIL,
                        API_KEY,
                        type,
                        code == null ? "" : ",\"code\":\"" + code + "\"");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "text-embedding-3-small",
                "text-embedding-3-large",
                "text-embedding-ada-002"
            })
    void documentedModelsReportTheirInputLimits(String model) {
        try (OpenAiEmbeddingProvider documented =
                provider(model, server.baseUrl(), Duration.ofSeconds(5))) {
            assertThat(documented.limits()).isEqualTo(new InputLimits(8192, 2048, 300_000));
        }
    }

    @Test
    void modelWithoutDocumentedLimitIsRefused() {
        assertThatThrownBy(() -> provider("other-model", server.baseUrl(), Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.embeddings.model has no known input limit")
                .hasMessageNotContaining(API_KEY);
    }

    private static OpenAiEmbeddingProvider provider(URI baseUrl, Duration timeout) {
        return provider("text-embedding-3-small", baseUrl, timeout);
    }

    private static OpenAiEmbeddingProvider provider(String model, URI baseUrl, Duration timeout) {
        return new OpenAiEmbeddingProvider(
                new ProviderProperties(
                        Provider.OPENAI,
                        model,
                        DIMENSIONS,
                        64,
                        4,
                        Duration.ofSeconds(60),
                        50_000,
                        timeout,
                        baseUrl,
                        API_KEY),
                CLOCK);
    }

    private static AbstractThrowableAssert<?, EmbeddingFailure> assertFailure(
            ThrowingCallable call, Type type, @Nullable Integer status) {
        EmbeddingFailure failure = catchThrowableOfType(EmbeddingFailure.class, call);
        assertThat(failure).as("expected an EmbeddingFailure").isNotNull();
        assertThat(failure.type()).isEqualTo(type);
        assertThat(failure.status()).isEqualTo(status);
        return assertThat(failure);
    }
}
