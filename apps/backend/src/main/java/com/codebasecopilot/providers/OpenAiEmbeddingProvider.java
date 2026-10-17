package com.codebasecopilot.providers;

import com.codebasecopilot.providers.EmbeddingFailure.Type;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Calls the SDK directly: usage is optional in the response (D-054), and the client is built
// by OpenAiClients, without retries or logging (D-047, D-051).
final class OpenAiEmbeddingProvider implements EmbeddingProvider, AutoCloseable {

    static final String NAME = "openai";

    // Embeddings guide and API reference (developers.openai.com, consulted on 16/10/2026):
    // 8192 tokens per input, 2048 inputs and 300,000 tokens summed per request (D-054).
    private static final Map<String, Integer> MAX_INPUT_TOKENS =
            Map.of(
                    "text-embedding-3-small", 8192,
                    "text-embedding-3-large", 8192,
                    "text-embedding-ada-002", 8192);
    private static final int MAX_INPUTS_PER_REQUEST = 2048;
    private static final int MAX_TOKENS_PER_REQUEST = 300_000;

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingProvider.class);

    private final OpenAIClient client;
    private final ModelInfo model;
    private final InputLimits limits;
    private final RequestOptions requestOptions;
    private final Clock clock;

    OpenAiEmbeddingProvider(ProviderProperties properties, Clock clock) {
        this.model = new ModelInfo(NAME, properties.model(), properties.dimensions());
        this.limits = limitsOf(properties.model());
        this.clock = clock;
        String apiKey = properties.apiKey();
        if (apiKey == null) {
            throw new IllegalStateException("app.embeddings.api-key is required");
        }
        this.client = OpenAiClients.create(properties.baseUrl(), apiKey, properties.timeout());
        this.requestOptions = OpenAiClients.requestOptions(properties.timeout());
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
    public InputLimits limits() {
        return limits;
    }

    @Override
    public void close() {
        client.close();
    }

    // Without a documented limit, a chunk could exceed what the model accepts.
    private static InputLimits limitsOf(String model) {
        Integer maxInputTokens = MAX_INPUT_TOKENS.get(model);
        if (maxInputTokens == null) {
            throw new IllegalStateException(
                    "app.embeddings.model has no known input limit for provider openai"
                            + " (supported: "
                            + String.join(", ", new TreeSet<>(MAX_INPUT_TOKENS.keySet()))
                            + ")");
        }
        return new InputLimits(maxInputTokens, MAX_INPUTS_PER_REQUEST, MAX_TOKENS_PER_REQUEST);
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
                    case 429 ->
                            OpenAiClients.quotaExceeded(e)
                                    ? Type.QUOTA_EXCEEDED
                                    : Type.RATE_LIMITED;
                    case 408, 409 -> Type.UNAVAILABLE;
                    default -> status >= 500 ? Type.UNAVAILABLE : Type.INVALID_REQUEST;
                };
        Duration retryAfter =
                type == Type.RATE_LIMITED || type == Type.UNAVAILABLE
                        ? retryAfter(e.headers())
                        : null;
        return new EmbeddingFailure(type, status, retryAfter, "embedding provider error");
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
