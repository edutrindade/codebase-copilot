package com.codebasecopilot.providers;

import org.jspecify.annotations.Nullable;

// Messages carry only the type and HTTP status: never the prompt, the output, the key or the
// provider's response body, which is why the provider exception is not kept as cause (D-054).
public class GenerationFailure extends RuntimeException {

    public enum Type {
        UNAUTHORIZED,
        RATE_LIMITED,
        // Credits, spend or usage limit exhausted (D-051).
        QUOTA_EXCEEDED,
        UNAVAILABLE,
        INVALID_REQUEST,
        // The output token limit was reached before the JSON document was complete.
        OUTPUT_TRUNCATED,
        // The model declined to answer, or a content filter removed the output.
        REFUSED
    }

    private final Type type;
    private final @Nullable Integer status;

    public GenerationFailure(Type type, String detail) {
        this(type, null, detail);
    }

    public GenerationFailure(Type type, @Nullable Integer status, String detail) {
        super(type + (status == null ? "" : " (HTTP " + status + ")") + ": " + detail);
        this.type = type;
        this.status = status;
    }

    public Type type() {
        return type;
    }

    public @Nullable Integer status() {
        return status;
    }
}
