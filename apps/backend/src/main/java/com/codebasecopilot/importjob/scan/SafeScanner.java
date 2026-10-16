package com.codebasecopilot.importjob.scan;

import com.codebasecopilot.codeanalysis.LanguageDetector;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

// Reads files only as bytes, never follows links and never opens special files (D-012, D-037).
class SafeScanner {

    static final int SAMPLE_BYTES = 8 * 1024;
    static final int EXAMPLES = 20;

    record Limits(int maxEntries, int maxDepth, long maxFileBytes) {}

    record Rules(List<ExclusionRule> defaults, List<PathPattern> custom) {}

    @FunctionalInterface
    interface Sampler {
        byte[] read(Path file, int maxBytes) throws IOException;
    }

    private enum State {
        INCLUDED,
        EXCLUDED,
        UNSUPPORTED,
        UNREADABLE
    }

    private final Sampler sampler;

    SafeScanner() {
        this(SafeScanner::readSample);
    }

    SafeScanner(Sampler sampler) {
        this.sampler = sampler;
    }

    // root must be a real directory already confined (ConfinedPaths).
    ScanResult scan(Path root, Rules rules, Limits limits) throws IOException {
        Walk walk = new Walk(root, rules, limits);
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, walk);
        return walk.result();
    }

    private static byte[] readSample(Path file, int maxBytes) throws IOException {
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            return in.readNBytes(maxBytes);
        }
    }

    private final class Walk extends SimpleFileVisitor<Path> {

        private final Path root;
        private final Rules rules;
        private final Limits limits;
        private final Map<String, long[]> languages = new TreeMap<>();
        private final Map<String, long[]> directories = new TreeMap<>();
        private final Map<GroupKey, Group> groups = new TreeMap<>();
        private long files;
        private long includedFiles;
        private long includedBytes;
        private long excludedFiles;
        private long unsupportedFiles;
        private long unreadableFiles;
        private long excludedDirectories;
        private int entries;
        private boolean truncated;

        Walk(Path root, Rules rules, Limits limits) {
            this.root = root;
            this.rules = rules;
            this.limits = limits;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
            if (dir.equals(root)) {
                return FileVisitResult.CONTINUE;
            }
            if (++entries > limits.maxEntries()) {
                truncated = true;
                return FileVisitResult.TERMINATE;
            }
            String path = relative(dir);
            Exclusion exclusion =
                    depth(path) > limits.maxDepth()
                            ? new Exclusion(ExclusionReason.DEPTH_LIMIT, null, null)
                            : ruleExclusion(path, true);
            if (exclusion == null) {
                return FileVisitResult.CONTINUE;
            }
            excludeDirectory(path, exclusion);
            return FileVisitResult.SKIP_SUBTREE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (++entries > limits.maxEntries()) {
                truncated = true;
                return FileVisitResult.TERMINATE;
            }
            String path = relative(file);
            if (attributes.isSymbolicLink()) {
                exclude(path, State.EXCLUDED, new Exclusion(ExclusionReason.SYMLINK, null, null));
            } else if (attributes.isOther() || attributes.isDirectory()) {
                exclude(
                        path,
                        State.EXCLUDED,
                        new Exclusion(ExclusionReason.SPECIAL_FILE, null, null));
            } else if (depth(path) > limits.maxDepth()) {
                exclude(
                        path,
                        State.EXCLUDED,
                        new Exclusion(ExclusionReason.DEPTH_LIMIT, null, null));
            } else {
                Exclusion exclusion = ruleExclusion(path, false);
                if (exclusion != null) {
                    exclude(path, State.EXCLUDED, exclusion);
                } else if (attributes.size() > limits.maxFileBytes()) {
                    exclude(
                            path,
                            State.EXCLUDED,
                            new Exclusion(ExclusionReason.TOO_LARGE, null, null));
                } else {
                    inspect(file, path, attributes.size());
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            if (++entries > limits.maxEntries()) {
                truncated = true;
                return FileVisitResult.TERMINATE;
            }
            String path = relative(file);
            Exclusion unreadable = new Exclusion(ExclusionReason.UNREADABLE, null, null);
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                excludeDirectory(path, unreadable);
            } else {
                exclude(path, State.UNREADABLE, unreadable);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) {
            return FileVisitResult.CONTINUE;
        }

        private void inspect(Path file, String path, long size) {
            byte[] sample;
            try {
                sample = sampler.read(file, SAMPLE_BYTES);
            } catch (IOException e) {
                exclude(
                        path,
                        State.UNREADABLE,
                        new Exclusion(ExclusionReason.UNREADABLE, null, null));
                return;
            }
            for (byte b : sample) {
                if (b == 0) {
                    exclude(
                            path,
                            State.UNSUPPORTED,
                            new Exclusion(ExclusionReason.BINARY, null, null));
                    return;
                }
            }
            String language = LanguageDetector.detect(Objects.toString(file.getFileName()), sample);
            long[] counts = languages.computeIfAbsent(language, k -> new long[2]);
            counts[0]++;
            counts[1] += size;
            files++;
            includedFiles++;
            includedBytes += size;
            directory(path)[State.INCLUDED.ordinal()]++;
        }

        private @Nullable Exclusion ruleExclusion(String path, boolean directory) {
            Optional<ExclusionRule> rule =
                    DefaultExclusionRules.match(rules.defaults(), path, directory);
            if (rule.isPresent()) {
                return new Exclusion(ExclusionReason.DEFAULT_RULE, rule.get().id(), null);
            }
            return rules.custom().stream()
                    .filter(p -> p.matches(path, directory))
                    .findFirst()
                    .map(p -> new Exclusion(ExclusionReason.CUSTOM_PATTERN, null, p.source()))
                    .orElse(null);
        }

        private void exclude(String path, State state, Exclusion exclusion) {
            files++;
            switch (state) {
                case EXCLUDED -> excludedFiles++;
                case UNSUPPORTED -> unsupportedFiles++;
                case UNREADABLE -> unreadableFiles++;
                case INCLUDED -> throw new IllegalArgumentException("not an exclusion");
            }
            directory(path)[state.ordinal()]++;
            group(exclusion).add(path, false);
        }

        private void excludeDirectory(String path, Exclusion exclusion) {
            excludedDirectories++;
            group(exclusion).add(path, true);
        }

        private Group group(Exclusion exclusion) {
            return groups.computeIfAbsent(
                    new GroupKey(exclusion.reason(), exclusion.ruleId(), exclusion.pattern()),
                    k -> new Group());
        }

        private long[] directory(String path) {
            int slash = path.indexOf('/');
            return directories.computeIfAbsent(
                    slash < 0 ? "" : path.substring(0, slash), k -> new long[4]);
        }

        private String relative(Path path) {
            return Normalizer.normalize(root.relativize(path).toString(), Normalizer.Form.NFC);
        }

        private static int depth(String path) {
            return (int) path.chars().filter(c -> c == '/').count() + 1;
        }

        ScanResult result() {
            List<ScanResult.LanguageCount> byLanguage = new ArrayList<>();
            languages.forEach(
                    (language, c) ->
                            byLanguage.add(new ScanResult.LanguageCount(language, c[0], c[1])));
            byLanguage.sort(
                    Comparator.comparingLong(ScanResult.LanguageCount::files)
                            .reversed()
                            .thenComparing(ScanResult.LanguageCount::language));
            List<ScanResult.DirectoryCount> byDirectory = new ArrayList<>();
            directories.forEach(
                    (path, c) ->
                            byDirectory.add(
                                    new ScanResult.DirectoryCount(path, c[0], c[1], c[2], c[3])));
            List<ScanResult.ExclusionGroup> exclusions = new ArrayList<>();
            groups.forEach(
                    (key, group) ->
                            exclusions.add(
                                    new ScanResult.ExclusionGroup(
                                            key.reason(),
                                            key.ruleId(),
                                            key.pattern(),
                                            group.files,
                                            group.directories,
                                            List.copyOf(group.examples))));
            return new ScanResult(
                    truncated,
                    new ScanResult.Totals(
                            files,
                            includedFiles,
                            includedBytes,
                            excludedFiles,
                            unsupportedFiles,
                            unreadableFiles,
                            excludedDirectories),
                    byLanguage,
                    byDirectory,
                    exclusions);
        }
    }

    private record Exclusion(
            ExclusionReason reason, @Nullable String ruleId, @Nullable String pattern) {}

    private record GroupKey(
            ExclusionReason reason, @Nullable String ruleId, @Nullable String pattern)
            implements Comparable<GroupKey> {

        private static final Comparator<GroupKey> ORDER =
                Comparator.comparing(GroupKey::reason)
                        .thenComparing(
                                GroupKey::ruleId, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(
                                GroupKey::pattern,
                                Comparator.nullsFirst(Comparator.naturalOrder()));

        @Override
        public int compareTo(GroupKey other) {
            return ORDER.compare(this, other);
        }
    }

    // Keeps the lexicographically smallest examples, so results are deterministic.
    private static final class Group {
        private long files;
        private long directories;
        private final TreeSet<String> examples = new TreeSet<>();

        void add(String path, boolean directory) {
            if (directory) {
                directories++;
            } else {
                files++;
            }
            examples.add(path);
            if (examples.size() > EXAMPLES) {
                examples.pollLast();
            }
        }
    }
}
