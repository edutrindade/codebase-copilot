package com.codebasecopilot.providers;

import org.jspecify.annotations.Nullable;

// output is the JSON document produced by the model, not yet validated against the schema.
// Token counts are the usage reported by the provider, or null when it reports none (D-054);
// outputTokens already includes reasoningTokens.
public record GenerationResult(
        String output,
        @Nullable Long inputTokens,
        @Nullable Long outputTokens,
        @Nullable Long reasoningTokens) {}
