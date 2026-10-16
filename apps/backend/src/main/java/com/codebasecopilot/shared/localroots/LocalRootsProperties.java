package com.codebasecopilot.shared.localroots;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app")
public record LocalRootsProperties(
        @Nullable String projectsRoot,
        @Nullable String projectsRootLabel,
        @DefaultValue List<Entry> localRoots) {

    public record Entry(@Nullable String key, @Nullable String label, @Nullable String path) {}
}
