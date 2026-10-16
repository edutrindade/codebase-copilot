package com.codebasecopilot.importjob;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

// Defaults are hypotheses to adjust with measurements (D-028).
@ConfigurationProperties("app.import")
record ImportProperties(
        @DefaultValue("100MB") DataSize maxZipBytes,
        @DefaultValue("50000") int maxEntries,
        @DefaultValue("1GB") DataSize maxUncompressedBytes,
        @DefaultValue("100MB") DataSize maxEntryBytes,
        @DefaultValue("100") long maxCompressionRatio,
        @DefaultValue("1MB") DataSize compressionRatioMinBytes,
        @DefaultValue("64") int maxDepth,
        @DefaultValue("1024") int maxPathLength,
        @DefaultValue("2") int maxConcurrent) {

    ZipExtractor.Limits limits() {
        return new ZipExtractor.Limits(
                maxEntries,
                maxUncompressedBytes.toBytes(),
                maxEntryBytes.toBytes(),
                maxCompressionRatio,
                compressionRatioMinBytes.toBytes(),
                maxDepth,
                maxPathLength);
    }
}
