package com.codebasecopilot.providers;

import com.codebasecopilot.providers.GenerationFailure.Type;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.core.RequestOptions;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletion.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Chat Completions with a strict JSON Schema and store=false, so the provider keeps no
// application state (D-063). One call per request: no retries (D-051).
final class OpenAiGenerationProvider implements GenerationProvider, AutoCloseable {

    static final String NAME = "openai";

    private static final Logger log = LoggerFactory.getLogger(OpenAiGenerationProvider.class);

    private final OpenAIClient client;
    private final RequestOptions requestOptions;
    private final GenerationModel model;
    private final int maxOutputTokens;
    private final @Nullable ReasoningEffort reasoningEffort;

    OpenAiGenerationProvider(GenerationProperties properties) {
        String apiKey = properties.apiKey();
        if (apiKey == null) {
            throw new IllegalStateException("app.generation.api-key is required");
        }
        this.client = OpenAiClients.create(properties.baseUrl(), apiKey, properties.timeout());
        this.requestOptions = OpenAiClients.requestOptions(properties.timeout());
        this.model = new GenerationModel(NAME, properties.model());
        this.maxOutputTokens = properties.maxOutputTokens();
        this.reasoningEffort =
                properties.reasoningEffort().isBlank()
                        ? null
                        : ReasoningEffort.of(properties.reasoningEffort());
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        ChatCompletionCreateParams.Builder params =
                ChatCompletionCreateParams.builder()
                        .model(model.model())
                        .addSystemMessage(request.instructions())
                        .addUserMessage(request.input())
                        .maxCompletionTokens(maxOutputTokens)
                        .responseFormat(format(request.schema()))
                        .store(false);
        if (reasoningEffort != null) {
            params.reasoningEffort(reasoningEffort);
        }
        long start = System.nanoTime();
        try {
            GenerationResult result =
                    read(client.chat().completions().create(params.build(), requestOptions));
            log.debug(
                    "Generated {} output tokens in {} ms",
                    result.outputTokens(),
                    Duration.ofNanos(System.nanoTime() - start).toMillis());
            return result;
        } catch (OpenAIServiceException e) {
            throw translate(e);
        } catch (OpenAIInvalidDataException e) {
            throw invalidResponse("response could not be read");
        } catch (OpenAIException e) {
            // I/O failures, including the per-call timeout.
            throw new GenerationFailure(Type.UNAVAILABLE, "request failed or timed out");
        }
    }

    @Override
    public GenerationModel model() {
        return model;
    }

    @Override
    public void close() {
        client.close();
    }

    private static ResponseFormatJsonSchema format(GenerationRequest.OutputSchema schema) {
        ResponseFormatJsonSchema.JsonSchema.Schema.Builder body =
                ResponseFormatJsonSchema.JsonSchema.Schema.builder();
        schema.jsonSchema()
                .forEach((key, value) -> body.putAdditionalProperty(key, JsonValue.from(value)));
        return ResponseFormatJsonSchema.builder()
                .jsonSchema(
                        ResponseFormatJsonSchema.JsonSchema.builder()
                                .name(schema.name())
                                .strict(true)
                                .schema(body.build())
                                .build())
                .build();
    }

    // A refusal or a truncated document is never returned as output (D-063).
    private static GenerationResult read(ChatCompletion completion) {
        if (completion.choices().isEmpty()) {
            throw invalidResponse("returned no choices");
        }
        ChatCompletion.Choice choice = completion.choices().getFirst();
        ChatCompletionMessage message = choice.message();
        FinishReason finish = choice.finishReason();
        if (message.refusal().filter(refusal -> !refusal.isBlank()).isPresent()
                || FinishReason.CONTENT_FILTER.equals(finish)) {
            throw new GenerationFailure(Type.REFUSED, "model refused or output was filtered");
        }
        if (FinishReason.LENGTH.equals(finish)) {
            throw new GenerationFailure(Type.OUTPUT_TRUNCATED, "output token limit reached");
        }
        String output =
                message.content()
                        .filter(content -> !content.isBlank())
                        .orElseThrow(() -> invalidResponse("returned no content"));
        Optional<CompletionUsage> usage = completion._usage().asKnown();
        return new GenerationResult(
                output,
                usage.flatMap(u -> u._promptTokens().asKnown()).orElse(null),
                usage.flatMap(u -> u._completionTokens().asKnown()).orElse(null),
                usage.flatMap(u -> u._completionTokensDetails().asKnown())
                        .flatMap(details -> details._reasoningTokens().asKnown())
                        .orElse(null));
    }

    private static GenerationFailure translate(OpenAIServiceException e) {
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
        return new GenerationFailure(type, status, "generation provider error");
    }

    private static GenerationFailure invalidResponse(String detail) {
        return new GenerationFailure(Type.INVALID_REQUEST, "provider " + detail);
    }
}
