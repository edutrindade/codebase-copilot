package com.codebasecopilot.importjob.scan;

import java.util.List;
import org.jspecify.annotations.Nullable;

// Aggregates only; included files are reported to SafeScanner.Listener.
public record ScanResult(
        boolean truncated,
        Totals totals,
        List<LanguageCount> byLanguage,
        List<DirectoryCount> byDirectory,
        List<ExclusionGroup> exclusions) {

    public record Totals(
            long files,
            long includedFiles,
            long includedBytes,
            long excludedFiles,
            long unsupportedFiles,
            long unreadableFiles,
            long excludedDirectories) {}

    public record LanguageCount(String language, long files, long bytes) {}

    public record DirectoryCount(
            String path,
            long includedFiles,
            long excludedFiles,
            long unsupportedFiles,
            long unreadableFiles) {}

    // Files inside an excluded directory are not visited, so they are not counted.
    public record ExclusionGroup(
            ExclusionReason reason,
            @Nullable String ruleId,
            @Nullable String pattern,
            long files,
            long directories,
            List<String> examples) {}
}
