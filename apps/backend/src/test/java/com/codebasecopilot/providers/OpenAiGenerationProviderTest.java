package com.codebasecopilot.providers;

import static com.codebasecopilot.providers.StubOpenAiServer.completion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.codebasecopilot.providers.GenerationFailure.Type;
import com.codebasecopilot.providers.GenerationRequest.OutputSchema;
import com.codebasecopilot.providers.ProviderProperties.Provider;
import com.codebasecopilot.providers.StubOpenAiServer.Reply;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
class OpenAiGenerationProviderTest {

    private static final String API_KEY = "sk-test-0123456789-must-not-leak";
    private static final String INSTRUCTIONS = "Answer only from the evidence.";
    private static final String INPUT = "E1 class PaymentService { secretChunkContent(); }";
    private static final String OUTPUT = "{\"answer\":\"secretOutputContent\"}";
    private static final String PROVIDER_DETAIL = "provider-detail-must-not-leak";
    private static final String USAGE =
            "{\"prompt_tokens\":120,\"completion_tokens\":40,\"total_tokens\":160,"
                    + "\"completion_tokens_details\":{\"reasoning_tokens\":25}}";
    private static final OutputSchema SCHEMA =
            new OutputSchema(
                    "answer",
                    Map.of(
                            "type",
                            "object",
                            "properties",
                            Map.of("answer", Map.of("type", "string")),
                            "required",
                            List.of("answer"),
                            "additionalProperties",
                            false));

    private StubOpenAiServer server;
    private OpenAiGenerationProvider provider;

    @BeforeEach
    void start() throws IOException {
        server = new StubOpenAiServer();
        provider = provider("low", server.baseUrl(), Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        provider.close();
        server.close();
    }

    @Test
    void returnsOutputAndReportedUsage() {
        server.reply(Reply.json(200, completion("stop", OUTPUT, null, USAGE)));

        GenerationResult result = provider.generate(request());

        assertThat(result).isEqualTo(new GenerationResult(OUTPUT, 120L, 40L, 25L));
    }

    @Test
    void sendsOneChatCompletionWithStrictSchemaAndNoStorage() {
        server.reply(Reply.json(200, completion("stop", OUTPUT, null, USAGE)));

        provider.generate(request());

        assertThat(server.requests())
                .singleElement()
                .satisfies(
                        request -> {
                            assertThat(request.path()).isEqualTo("/v1/chat/completions");
                            assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
                            assertThat(request.body())
                                    .contains("\"model\":\"gpt-6-luna\"")
                                    .contains("\"role\":\"system\"")
                                    .contains("\"content\":\"" + INSTRUCTIONS + "\"")
                                    .contains("\"role\":\"user\"")
                                    .contains("secretChunkContent")
                                    .contains("\"max_completion_tokens\":2000")
                                    .contains("\"reasoning_effort\":\"low\"")
                                    .contains("\"store\":false")
                                    .contains("\"type\":\"json_schema\"")
                                    .contains("\"name\":\"answer\"")
                                    .contains("\"strict\":true")
                                    .contains("\"additionalProperties\":false");
                        });
    }

    @Test
    void blankReasoningEffortIsNotSent() {
        server.reply(Reply.json(200, completion("stop", OUTPUT, null, USAGE)));

        try (OpenAiGenerationProvider plain =
                provider("", server.baseUrl(), Duration.ofSeconds(5))) {
            plain.generate(request());
        }

        assertThat(server.requests().getFirst().body()).doesNotContain("reasoning_effort");
    }

    @Test
    void reportsModel() {
        assertThat(provider.model()).isEqualTo(new GenerationModel("openai", "gpt-6-luna"));
    }

    @Test
    void tokensAreNullWhenUsageIsNotReported() {
        server.reply(Reply.json(200, completion("stop", OUTPUT, null, "")));

        assertThat(provider.generate(request()))
                .isEqualTo(new GenerationResult(OUTPUT, null, null, null));
    }

    @Test
    void reasoningTokensAreNullWhenNotDetailed() {
        server.reply(
                Reply.json(
                        200,
                        completion(
                                "stop",
                                OUTPUT,
                                null,
                                "{\"prompt_tokens\":10,\"completion_tokens\":5,"
                                        + "\"total_tokens\":15}")));

        assertThat(provider.generate(request()))
                .isEqualTo(new GenerationResult(OUTPUT, 10L, 5L, null));
    }

    @Test
    void lengthFinishIsTruncated() {
        server.reply(Reply.json(200, completion("length", "{\"answer\":\"cut", null, USAGE)));

        assertFailure(() -> provider.generate(request()), Type.OUTPUT_TRUNCATED, null);
    }

    @Test
    void refusalIsRefused() {
        server.reply(Reply.json(200, completion("stop", null, PROVIDER_DETAIL, USAGE)));

        assertFailure(() -> provider.generate(request()), Type.REFUSED, null)
                .hasMessageNotContaining(PROVIDER_DETAIL);
    }

    @Test
    void contentFilterIsRefused() {
        server.reply(Reply.json(200, completion("content_filter", null, null, USAGE)));

        assertFailure(() -> provider.generate(request()), Type.REFUSED, null);
    }

    @Test
    void missingContentIsInvalid() {
        server.reply(Reply.json(200, completion("stop", null, null, USAGE)));

        assertFailure(() -> provider.generate(request()), Type.INVALID_REQUEST, null)
                .hasMessageContaining("no content");
    }

    @Test
    void noChoicesIsInvalid() {
        server.reply(
                Reply.json(
                        200,
                        "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\","
                                + "\"created\":1760000000,\"model\":\"gpt-6-luna\","
                                + "\"choices\":[]}"));

        assertFailure(() -> provider.generate(request()), Type.INVALID_REQUEST, null)
                .hasMessageContaining("no choices");
    }

    @Test
    void unreadableResponseIsInvalid() {
        server.reply(Reply.json(200, "{\"choices\":\"" + PROVIDER_DETAIL + "\"}"));

        assertFailure(() -> provider.generate(request()), Type.INVALID_REQUEST, null);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void credentialErrorsAreUnauthorized(int status) {
        server.reply(Reply.json(status, error("invalid_request_error", null)));

        assertFailure(() -> provider.generate(request()), Type.UNAUTHORIZED, status);
    }

    @Test
    void quotaCodeIsQuotaExceeded() {
        server.reply(Reply.json(429, error("insufficient_quota", "credit_balance_exhausted")));

        assertFailure(() -> provider.generate(request()), Type.QUOTA_EXCEEDED, 429);
    }

    @Test
    void otherTooManyRequestsIsRateLimited() {
        server.reply(Reply.json(429, error("rate_limit_error", "slow_down")));

        assertFailure(() -> provider.generate(request()), Type.RATE_LIMITED, 429);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 408})
    void serverErrorsAreUnavailableAndNotRetried(int status) {
        server.reply(Reply.json(status, error("server_error", null)));

        assertFailure(() -> provider.generate(request()), Type.UNAVAILABLE, status);
        assertThat(server.requests()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 422})
    void requestErrorsAreInvalidRequest(int status) {
        server.reply(Reply.json(status, error("invalid_request_error", null)));

        assertFailure(() -> provider.generate(request()), Type.INVALID_REQUEST, status);
    }

    @Test
    void timeoutIsUnavailable() {
        server.reply(
                Reply.json(200, completion("stop", OUTPUT, null, USAGE))
                        .delayedBy(Duration.ofSeconds(10)));

        try (OpenAiGenerationProvider impatient =
                provider("low", server.baseUrl(), Duration.ofMillis(200))) {
            long start = System.nanoTime();
            assertFailure(() -> impatient.generate(request()), Type.UNAVAILABLE, null);
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
        try (OpenAiGenerationProvider unreachable =
                provider(
                        "low",
                        URI.create("http://127.0.0.1:" + port + "/v1"),
                        Duration.ofSeconds(2))) {
            assertFailure(() -> unreachable.generate(request()), Type.UNAVAILABLE, null);
        }
    }

    @Test
    void failuresNeverExposePromptOutputKeyOrProviderBody(CapturedOutput output) {
        List<Reply> replies =
                List.of(
                        Reply.json(400, error("invalid_request_error", null)),
                        Reply.json(401, error("invalid_request_error", null)),
                        Reply.json(429, error("rate_limit_error", null)),
                        Reply.json(429, error("insufficient_quota", "credit_balance_exhausted")),
                        Reply.json(500, error("server_error", null)),
                        Reply.json(200, completion("length", OUTPUT, null, USAGE)),
                        Reply.json(200, completion("stop", null, PROVIDER_DETAIL, USAGE)),
                        Reply.json(200, "{\"choices\":\"" + PROVIDER_DETAIL + "\"}"));

        for (Reply reply : replies) {
            server.reply(reply);
            GenerationFailure failure =
                    catchThrowableOfType(
                            GenerationFailure.class, () -> provider.generate(request()));
            assertThat(failure).isNotNull();
            assertThat(failure.getCause()).isNull();
            assertThat(failure.getMessage())
                    .doesNotContain(API_KEY)
                    .doesNotContain(PROVIDER_DETAIL)
                    .doesNotContain("secretChunkContent")
                    .doesNotContain("secretOutputContent");
        }
        server.reply(Reply.json(200, completion("stop", OUTPUT, null, USAGE)));
        provider.generate(request());

        assertThat(output.getAll())
                .doesNotContain(API_KEY)
                .doesNotContain(PROVIDER_DETAIL)
                .doesNotContain("secretChunkContent")
                .doesNotContain("secretOutputContent")
                .doesNotContain(INSTRUCTIONS);
    }

    private static GenerationRequest request() {
        return new GenerationRequest(INSTRUCTIONS, INPUT, SCHEMA);
    }

    private static String error(String type, @Nullable String code) {
        return "{\"error\":{\"message\":\"%s %s\",\"type\":\"%s\"%s}}"
                .formatted(
                        PROVIDER_DETAIL,
                        API_KEY,
                        type,
                        code == null ? "" : ",\"code\":\"" + code + "\"");
    }

    private static OpenAiGenerationProvider provider(
            String reasoningEffort, URI baseUrl, Duration timeout) {
        return new OpenAiGenerationProvider(
                new GenerationProperties(
                        Provider.OPENAI,
                        "gpt-6-luna",
                        reasoningEffort,
                        2000,
                        timeout,
                        baseUrl,
                        API_KEY));
    }

    private static AbstractThrowableAssert<?, GenerationFailure> assertFailure(
            ThrowingCallable call, Type type, @Nullable Integer status) {
        GenerationFailure failure = catchThrowableOfType(GenerationFailure.class, call);
        assertThat(failure).as("expected a GenerationFailure").isNotNull();
        assertThat(failure.type()).isEqualTo(type);
        assertThat(failure.status()).isEqualTo(status);
        return assertThat(failure);
    }
}
