package com.codebasecopilot.providers;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Defaults are hypotheses to adjust with measurements (D-047, D-051, D-054).
@ConfigurationProperties("app.embeddings")
public record ProviderProperties(
        @DefaultValue("none") Provider provider,
        @DefaultValue("text-embedding-3-small") String model,
        @DefaultValue("1536") int dimensions,
        @DefaultValue("64") int batchSize,
        @DefaultValue("4") int maxRetries,
        @DefaultValue("60s") Duration maxRetryWait,
        @DefaultValue("50000") int maxChunksPerJob,
        @DefaultValue("30s") Duration timeout,
        @DefaultValue(OFFICIAL_BASE_URL) URI baseUrl,
        @Nullable String apiKey) {

    public static final String OFFICIAL_BASE_URL = "https://api.openai.com/v1";

    // pgvector cannot build an HNSW index on vector columns above this (O-004, D-048).
    public static final int MAX_DIMENSIONS = 2000;

    public enum Provider {
        NONE,
        FAKE,
        OPENAI
    }

    public ProviderProperties {
        if (model.isBlank()) {
            throw invalid("model must not be blank");
        }
        if (dimensions < 1 || dimensions > MAX_DIMENSIONS) {
            throw invalid("dimensions must be between 1 and " + MAX_DIMENSIONS);
        }
        if (batchSize < 1) {
            throw invalid("batch-size must be positive");
        }
        if (maxRetries < 0) {
            throw invalid("max-retries must not be negative");
        }
        if (!isPositive(maxRetryWait)) {
            throw invalid("max-retry-wait must be positive");
        }
        if (maxChunksPerJob < 1) {
            throw invalid("max-chunks-per-job must be positive");
        }
        if (!isPositive(timeout)) {
            throw invalid("timeout must be positive");
        }
        OpenAiClients.validateBaseUrl(baseUrl, "app.embeddings.base-url");
        if (provider == Provider.OPENAI && (apiKey == null || apiKey.isBlank())) {
            throw invalid("api-key is required when provider is openai (set EMBEDDING_API_KEY)");
        }
    }

    public boolean enabled() {
        return provider != Provider.NONE;
    }

    // Records print every component; the key must never reach logs or error messages.
    @Override
    public String toString() {
        return ("ProviderProperties[provider=%s, model=%s, dimensions=%d, batchSize=%d,"
                        + " maxRetries=%d, maxRetryWait=%s, maxChunksPerJob=%d, timeout=%s,"
                        + " baseUrl=%s, apiKey=%s]")
                .formatted(
                        provider,
                        model,
                        dimensions,
                        batchSize,
                        maxRetries,
                        maxRetryWait,
                        maxChunksPerJob,
                        timeout,
                        baseUrl,
                        apiKey == null || apiKey.isBlank() ? "<none>" : "<redacted>");
    }

    private static boolean isPositive(Duration duration) {
        return !duration.isNegative() && !duration.isZero();
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("app.embeddings." + message);
    }
}
