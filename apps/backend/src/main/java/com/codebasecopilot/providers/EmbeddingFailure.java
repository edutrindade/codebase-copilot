package com.codebasecopilot.providers;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

// Messages carry only the type, HTTP status and counts: never the texts, the key or the
// provider's response body, which is why the provider exception is not kept as cause (D-054).
public class EmbeddingFailure extends RuntimeException {

    public enum Type {
        UNAUTHORIZED,
        RATE_LIMITED,
        // Credits, spend or usage limit exhausted: retrying does not restore access (D-051).
        QUOTA_EXCEEDED,
        UNAVAILABLE,
        INVALID_REQUEST
    }

    private final Type type;
    private final @Nullable Integer status;
    private final @Nullable Duration retryAfter;

    public EmbeddingFailure(Type type, String detail) {
        this(type, null, null, detail);
    }

    public EmbeddingFailure(
            Type type, @Nullable Integer status, @Nullable Duration retryAfter, String detail) {
        super(type + (status == null ? "" : " (HTTP " + status + ")") + ": " + detail);
        this.type = type;
        this.status = status;
        this.retryAfter = retryAfter;
    }

    public Type type() {
        return type;
    }

    public @Nullable Integer status() {
        return status;
    }

    // Delay asked by the provider (Retry-After), before the max-retry-wait cap.
    public @Nullable Duration retryAfter() {
        return retryAfter;
    }

    public boolean retryable() {
        return type == Type.RATE_LIMITED || type == Type.UNAVAILABLE;
    }
}
