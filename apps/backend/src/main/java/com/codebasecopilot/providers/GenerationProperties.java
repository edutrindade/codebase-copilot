package com.codebasecopilot.providers;

import com.codebasecopilot.providers.ProviderProperties.Provider;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Chat provider for answers (D-063), configured apart from embeddings: the key, the model and
// the destination of the data are independent. Defaults are hypotheses measured in the answer
// evaluation.
@ConfigurationProperties("app.generation")
public record GenerationProperties(
        @DefaultValue("none") Provider provider,
        @DefaultValue("gpt-6-luna") String model,
        // Blank omits the parameter, for compatible servers that reject it.
        @DefaultValue("low") String reasoningEffort,
        // Includes reasoning tokens, which are billed as output.
        @DefaultValue("2000") int maxOutputTokens,
        @DefaultValue("60s") Duration timeout,
        @DefaultValue(ProviderProperties.OFFICIAL_BASE_URL) URI baseUrl,
        @Nullable String apiKey) {

    static final Set<String> REASONING_EFFORTS =
            Set.of("none", "minimal", "low", "medium", "high", "xhigh", "max");

    public GenerationProperties {
        if (model.isBlank()) {
            throw invalid("model must not be blank");
        }
        if (!reasoningEffort.isBlank() && !REASONING_EFFORTS.contains(reasoningEffort)) {
            throw invalid("reasoning-effort must be blank or one of " + REASONING_EFFORTS);
        }
        if (maxOutputTokens < 1) {
            throw invalid("max-output-tokens must be positive");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw invalid("timeout must be positive");
        }
        OpenAiClients.validateBaseUrl(baseUrl, "app.generation.base-url");
        if (provider == Provider.OPENAI && (apiKey == null || apiKey.isBlank())) {
            throw invalid("api-key is required when provider is openai (set GENERATION_API_KEY)");
        }
    }

    public boolean enabled() {
        return provider != Provider.NONE;
    }

    // Records print every component; the key must never reach logs or error messages.
    @Override
    public String toString() {
        return ("GenerationProperties[provider=%s, model=%s, reasoningEffort=%s,"
                        + " maxOutputTokens=%d, timeout=%s, baseUrl=%s, apiKey=%s]")
                .formatted(
                        provider,
                        model,
                        reasoningEffort,
                        maxOutputTokens,
                        timeout,
                        baseUrl,
                        apiKey == null || apiKey.isBlank() ? "<none>" : "<redacted>");
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("app.generation." + message);
    }
}
