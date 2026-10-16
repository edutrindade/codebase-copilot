package com.codebasecopilot.importjob.scan;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

// Defaults are hypotheses to measure in Etapa 8 (D-035).
@ConfigurationProperties("app.inventory")
record InventoryProperties(
        @DefaultValue("200000") int maxEntries,
        @DefaultValue("64") int maxDepth,
        @DefaultValue("1MB") DataSize maxFileBytes,
        @DefaultValue("500MB") DataSize maxProjectBytes,
        @DefaultValue("10MB") DataSize maxFileBytesLimit,
        @DefaultValue("2GB") DataSize maxProjectBytesLimit) {}
