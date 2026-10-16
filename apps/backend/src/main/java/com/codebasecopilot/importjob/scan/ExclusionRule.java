package com.codebasecopilot.importjob.scan;

import java.util.List;

// A default rule with a stable id; only overridable rules can be disabled per project (D-036).
public final class ExclusionRule {

    private final String id;
    private final ExclusionCategory category;
    private final List<String> patterns;
    private final boolean overridable;
    private final List<PathPattern> compiledPatterns;
    private final List<PathPattern> compiledExceptions;

    ExclusionRule(
            String id,
            ExclusionCategory category,
            List<String> patterns,
            List<String> exceptions,
            boolean overridable) {
        this.id = id;
        this.category = category;
        this.patterns = List.copyOf(patterns);
        this.overridable = overridable;
        this.compiledPatterns = patterns.stream().map(PathPattern::compile).toList();
        this.compiledExceptions = exceptions.stream().map(PathPattern::compile).toList();
    }

    public String id() {
        return id;
    }

    public ExclusionCategory category() {
        return category;
    }

    public List<String> patterns() {
        return patterns;
    }

    public boolean overridable() {
        return overridable;
    }

    boolean matches(String relativePath, boolean directory) {
        return compiledPatterns.stream().anyMatch(p -> p.matches(relativePath, directory))
                && compiledExceptions.stream().noneMatch(p -> p.matches(relativePath, directory));
    }
}
