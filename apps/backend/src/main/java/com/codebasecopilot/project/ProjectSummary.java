package com.codebasecopilot.project;

import org.jspecify.annotations.Nullable;

public record ProjectSummary(
        Project project, ProjectImports.@Nullable CurrentImport currentImport) {}
