package com.codebasecopilot.providers;

import com.codebasecopilot.providers.EmbeddingFailure.Type;
import com.openai.client.OpenAIClient;
import com.openai.core.LogLevel;
import com.openai.core.RequestOptions;
import com.openai.core.Timeout;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.setup.OpenAiSetup;

// The client comes from Spring AI's OpenAiSetup, but the call goes straight to the SDK: Spring
// AI's OpenAiEmbeddingModel fails when the response has no usage, and usage is optional (D-054).
final class OpenAiEmbeddingProvider implements EmbeddingProvider, AutoCloseable {

    static final String NAME = "openai";

    private static final String INSUFFICIENT_QUOTA = "insufficient_quota";
    private static final Set<String> QUOTA_CODES =
            Set.of(
                    "credit_balance_exhausted",
                    "organization_spend_limit_exceeded",
                    "project_spend_limit_exceeded",
                    "organization_usage_limit_exceeded");

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingProvider.class);

    private final OpenAIClient client;
    private final ModelInfo model;
    private final RequestOptions requestOptions;
    private final Clock clock;

    OpenAiEmbeddingProvider(ProviderProperties properties, Clock clock) {
        this.model = new ModelInfo(NAME, properties.model(), properties.dimensions());
        this.clock = clock;
        Duration timeout = properties.timeout();
        this.requestOptions =
                RequestOptions.builder()
                        .timeout(Timeout.builder().connect(timeout).request(timeout).build())
                        .build();
        // No SDK retries: EmbeddingRetry is the only retry layer (D-051). Logging off, since
        // OPENAI_LOG=debug in the environment would log request bodies.
        this.client =
                OpenAiSetup.setupSyncClient(
                                properties.baseUrl().toString(),
                                properties.apiKey(),
                                null,
                                null,
                                null,
                                null,
                                false,
                                false,
                                properties.model(),
                                timeout,
                                0,
                                null,
                                null,
                                ObservationRegistry.NOOP,
                                null,
                                List.of())
                        .withOptions(options -> options.maxRetries(0).logLevel(LogLevel.OFF));
    }

    @Override
    public EmbeddingResult embed(List<String> texts) {
        if (texts.isEmpty()) {
            return new EmbeddingResult(List.of(), 0L);
        }
        EmbeddingCreateParams params =
                EmbeddingCreateParams.builder()
                        .model(model.model())
                        .inputOfArrayOfStrings(texts)
                        .dimensions(model.dimensions())
                        .encodingFormat(EmbeddingCreateParams.EncodingFormat.FLOAT)
                        .build();
        long start = System.nanoTime();
        try {
            EmbeddingResult result =
                    read(client.embeddings().create(params, requestOptions), texts);
            log.debug(
                    "Embedded {} texts in {} ms",
                    texts.size(),
                    Duration.ofNanos(System.nanoTime() - start).toMillis());
            return result;
        } catch (OpenAIServiceException e) {
            throw translate(e);
        } catch (OpenAIInvalidDataException e) {
            throw invalidResponse("response could not be read");
        } catch (OpenAIException e) {
            // I/O failures, including the per-call timeout.
            throw new EmbeddingFailure(Type.UNAVAILABLE, "request failed or timed out");
        }
    }

    @Override
    public ModelInfo model() {
        return model;
    }

    @Override
    public void close() {
        client.close();
    }

    // A wrong vector must never be stored: any mismatch rejects the whole batch.
    private EmbeddingResult read(CreateEmbeddingResponse response, List<String> texts) {
        List<Embedding> data = response.data();
        int expected = texts.size();
        if (data.size() != expected) {
            throw invalidResponse(
                    "returned %d vectors for %d texts".formatted(data.size(), expected));
        }
        float[][] vectors = new float[expected][];
        for (Embedding embedding : data) {
            long index = embedding.index();
            if (index < 0 || index >= expected || vectors[(int) index] != null) {
                throw invalidResponse("returned an invalid or repeated vector index");
            }
            vectors[(int) index] = vector(embedding.embedding());
        }
        Long tokens =
                response._usage()
                        .asKnown()
                        .flatMap(
                                usage ->
                                        usage._totalTokens()
                                                .asKnown()
                                                .or(() -> usage._promptTokens().asKnown()))
                        .orElse(null);
        return new EmbeddingResult(Arrays.asList(vectors), tokens);
    }

    private float[] vector(List<Float> values) {
        if (values.size() != model.dimensions()) {
            throw invalidResponse(
                    "returned a vector with %d dimensions, expected %d"
                            .formatted(values.size(), model.dimensions()));
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < vector.length; i++) {
            float value = values.get(i);
            if (!Float.isFinite(value)) {
                throw invalidResponse("returned a non-finite vector component");
            }
            vector[i] = value;
        }
        return vector;
    }

    private EmbeddingFailure translate(OpenAIServiceException e) {
        int status = e.statusCode();
        Type type =
                switch (status) {
                    case 401, 403 -> Type.UNAUTHORIZED;
                    case 429 -> quotaExceeded(e) ? Type.QUOTA_EXCEEDED : Type.RATE_LIMITED;
                    case 408, 409 -> Type.UNAVAILABLE;
                    default -> status >= 500 ? Type.UNAVAILABLE : Type.INVALID_REQUEST;
                };
        Duration retryAfter =
                type == Type.RATE_LIMITED || type == Type.UNAVAILABLE
                        ? retryAfter(e.headers())
                        : null;
        return new EmbeddingFailure(type, status, retryAfter, "embedding provider error");
    }

    // A 429 is told apart by error.code; error.type may still be insufficient_quota (OpenAI
    // error codes guide, consulted on 16/10/2026). The code is read, never logged (D-051).
    private static boolean quotaExceeded(OpenAIServiceException e) {
        return e.code().filter(QUOTA_CODES::contains).isPresent()
                || e.type().filter(INSUFFICIENT_QUOTA::equals).isPresent();
    }

    private @Nullable Duration retryAfter(Headers headers) {
        try {
            List<String> millis = headers.values("retry-after-ms");
            if (!millis.isEmpty()) {
                return nonNegative(
                        Duration.ofNanos((long) (Double.parseDouble(millis.get(0)) * 1e6)));
            }
            List<String> values = headers.values("Retry-After");
            if (values.isEmpty()) {
                return null;
            }
            String value = values.get(0).trim();
            if (!value.isEmpty() && Character.isDigit(value.charAt(0))) {
                return nonNegative(Duration.ofMillis((long) (Double.parseDouble(value) * 1000)));
            }
            Instant at =
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration until = Duration.between(clock.instant(), at);
            return until.isNegative() ? Duration.ZERO : until;
        } catch (NumberFormatException | DateTimeParseException e) {
            return null;
        }
    }

    private static @Nullable Duration nonNegative(Duration duration) {
        return duration.isNegative() ? null : duration;
    }

    private static EmbeddingFailure invalidResponse(String detail) {
        return new EmbeddingFailure(Type.INVALID_REQUEST, "provider " + detail);
    }
}
