package com.codebasecopilot.shared.datadir;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record DataDirectoryProperties(@Nullable String dataDir) {}
