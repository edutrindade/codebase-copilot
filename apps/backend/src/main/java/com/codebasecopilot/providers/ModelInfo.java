package com.codebasecopilot.providers;

// Identifies the vectors a provider produces; vectors of different models never mix (D-048).
public record ModelInfo(String provider, String model, int dimensions) {}
