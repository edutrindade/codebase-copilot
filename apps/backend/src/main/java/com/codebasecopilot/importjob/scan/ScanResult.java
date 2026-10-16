package com.codebasecopilot.importjob.scan;

import java.util.List;
import org.jspecify.annotations.Nullable;

// Aggregates only: the inventory is not persisted before the files table of Etapa 3 (D-035).
record ScanResult(
        boolean truncated,
        Totals totals,
        List<LanguageCount> byLanguage,
        List<DirectoryCount> byDirectory,
        List<ExclusionGroup> exclusions) {

    record Totals(
            long files,
            long includedFiles,
            long includedBytes,
            long excludedFiles,
            long unsupportedFiles,
            long unreadableFiles,
            long excludedDirectories) {}

    record LanguageCount(String language, long files, long bytes) {}

    record DirectoryCount(
            String path,
            long includedFiles,
            long excludedFiles,
            long unsupportedFiles,
            long unreadableFiles) {}

    // Files inside an excluded directory are not visited, so they are not counted.
    record ExclusionGroup(
            ExclusionReason reason,
            @Nullable String ruleId,
            @Nullable String pattern,
            long files,
            long directories,
            List<String> examples) {}
}
