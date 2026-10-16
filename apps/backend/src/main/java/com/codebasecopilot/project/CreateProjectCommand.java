package com.codebasecopilot.project;

import org.jspecify.annotations.Nullable;

public record CreateProjectCommand(
        @Nullable String name,
        @Nullable SourceType sourceType,
        @Nullable String rootKey,
        @Nullable String relativePath) {}
