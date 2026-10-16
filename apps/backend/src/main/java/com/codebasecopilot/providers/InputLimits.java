package com.codebasecopilot.providers;

// Request limits of the configured model, checked at startup against the chunk size (D-054).
public record InputLimits(int maxInputTokens, int maxInputsPerRequest, int maxTokensPerRequest) {

    public static final InputLimits UNBOUNDED =
            new InputLimits(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
}
