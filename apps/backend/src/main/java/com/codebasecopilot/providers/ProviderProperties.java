package com.codebasecopilot.providers;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
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

    // The base URL decides where the key and code chunks are sent (D-047).
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1");

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
        validateBaseUrl(baseUrl);
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

    // Error messages never echo the value: a URL with user info could carry a credential.
    private static void validateBaseUrl(URI url) {
        String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || !(scheme.equals("https") || scheme.equals("http"))) {
            throw invalid("base-url must be an absolute http(s) URL");
        }
        if (url.getRawUserInfo() != null) {
            throw invalid("base-url must not contain user info");
        }
        if (scheme.equals("http") && !LOOPBACK_HOSTS.contains(host)) {
            throw invalid(
                    "base-url must use https (http is allowed only for localhost or 127.0.0.1)");
        }
    }

    private static boolean isPositive(Duration duration) {
        return !duration.isNegative() && !duration.isZero();
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("app.embeddings." + message);
    }
}
