package com.codebasecopilot.providers;

import java.util.List;
import org.jspecify.annotations.Nullable;

// tokens is the usage reported by the provider, or null when it reports none (D-054).
public record EmbeddingResult(List<float[]> vectors, @Nullable Long tokens) {

    public EmbeddingResult {
        vectors = List.copyOf(vectors);
    }
}
