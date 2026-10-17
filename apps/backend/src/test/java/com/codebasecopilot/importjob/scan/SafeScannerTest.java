package com.codebasecopilot.importjob.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.codebasecopilot.importjob.scan.ScanResult.DirectoryCount;
import com.codebasecopilot.importjob.scan.ScanResult.ExclusionGroup;
import com.codebasecopilot.importjob.scan.ScanResult.LanguageCount;
import com.codebasecopilot.importjob.scan.ScanResult.Totals;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

// Acceptance of Etapa 2: the scan never leaves the root, follows no link, opens no special file.
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class SafeScannerTest {

    private static final SafeScanner.Limits LIMITS = new SafeScanner.Limits(1000, 10, 1024);
    private static final SafeScanner.Rules DEFAULTS =
            new SafeScanner.Rules(DefaultExclusionRules.ALL, List.of());

    @TempDir Path tempDir;

    private Path root;
    private Path outside;
    private final Set<Path> sampled = ConcurrentHashMap.newKeySet();
    private final List<Path> locked = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        tempDir = tempDir.toRealPath();
        root = Files.createDirectory(tempDir.resolve("root"));
        outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");
    }

    @AfterEach
    void unlock() throws IOException {
        for (Path path : locked) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void classifiesEntriesByStateLanguageAndDirectory() throws IOException {
        write("src/Main.java", "class Main {}");
        write("src/util/helper.py", "x = 1");
        write("README.md", "# X");
        write("run", "#!/usr/bin/env python3\nprint(1)");
        write(".env", "SECRET=1");
        write(".env.example", "SECRET=");
        write("node_modules/lib/index.js", "module.exports = 1");
        write("big.txt", "x".repeat(2000));
        Files.write(root.resolve("data.bin"), new byte[] {1, 0, 2});
        Files.write(root.resolve("logo.png"), new byte[] {0, 0});

        ScanResult result = scan(DEFAULTS, LIMITS);

        assertThat(result.truncated()).isFalse();
        assertThat(result.totals()).isEqualTo(new Totals(9, 5, 13 + 5 + 3 + 31 + 7, 3, 1, 0, 1));
        assertThat(result.byLanguage())
                .containsExactly(
                        new LanguageCount("python", 2, 5 + 31),
                        new LanguageCount("java", 1, 13),
                        new LanguageCount("markdown", 1, 3),
                        new LanguageCount("text", 1, 7));
        assertThat(result.byDirectory())
                .containsExactly(
                        new DirectoryCount("", 3, 3, 1, 0), new DirectoryCount("src", 2, 0, 0, 0));
        assertThat(result.exclusions())
                .containsExactly(
                        new ExclusionGroup(
                                ExclusionReason.DEFAULT_RULE,
                                "env-files",
                                null,
                                1,
                                0,
                                List.of(".env")),
                        new ExclusionGroup(
                                ExclusionReason.DEFAULT_RULE,
                                "images",
                                null,
                                1,
                                0,
                                List.of("logo.png")),
                        new ExclusionGroup(
                                ExclusionReason.DEFAULT_RULE,
                                "node-modules",
                                null,
                                0,
                                1,
                                List.of("node_modules")),
                        new ExclusionGroup(
                                ExclusionReason.TOO_LARGE, null, null, 1, 0, List.of("big.txt")),
                        new ExclusionGroup(
                                ExclusionReason.BINARY, null, null, 1, 0, List.of("data.bin")));
        assertThat(sampled)
                .noneMatch(p -> p.startsWith(root.resolve("node_modules")))
                .doesNotContain(
                        root.resolve(".env"), root.resolve("big.txt"), root.resolve("logo.png"));
    }

    @Test
    void neverFollowsLinksInsideOrOutsideTheRoot() throws IOException {
        Path target = outside.resolve("secret.txt");
        if (isPosix()) {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("---------"));
            locked.add(target);
        }
        write("src/A.java", "class A {}");
        Files.createSymbolicLink(root.resolve("file-link"), target);
        Files.createSymbolicLink(root.resolve("dir-link"), outside);
        Files.createSymbolicLink(root.resolve("inside-link"), root.resolve("src"));
        Files.createSymbolicLink(root.resolve("loop-a"), root.resolve("loop-b"));
        Files.createSymbolicLink(root.resolve("loop-b"), root.resolve("loop-a"));

        ScanResult result = scan(DEFAULTS, LIMITS);

        assertThat(result.totals().includedFiles()).isEqualTo(1);
        assertThat(result.exclusions())
                .containsExactly(
                        new ExclusionGroup(
                                ExclusionReason.SYMLINK,
                                null,
                                null,
                                5,
                                0,
                                List.of(
                                        "dir-link",
                                        "file-link",
                                        "inside-link",
                                        "loop-a",
                                        "loop-b")));
        assertThat(sampled)
                .allMatch(p -> p.startsWith(root))
                .containsExactly(root.resolve("src/A.java"));
    }

    // Neither Git nor the ZIP extractor creates hard links, so one only exists if the user made
    // it; it is read as the regular file it is (documented limitation, D-082).
    @Test
    void readsAHardLinkAsARegularFile() throws IOException {
        Files.createLink(root.resolve("linked.txt"), outside.resolve("secret.txt"));

        ScanResult result = scan(DEFAULTS, LIMITS);

        assertThat(result.totals().includedFiles()).isEqualTo(1);
        assertThat(sampled).containsExactly(root.resolve("linked.txt"));
    }

    @Test
    void reportsNfdNamesInNfcAndKeepsTheRealFile() throws IOException {
        write("cafe\u0301/notas.md", "# Notas");
        List<String> reported = new ArrayList<>();

        new SafeScanner()
                .scan(
                        root,
                        DEFAULTS,
                        LIMITS,
                        (relativePath, file, sizeBytes, language) -> {
                            assertThat(file).isRegularFile();
                            reported.add(relativePath);
                        });

        assertThat(reported).containsExactly("caf\u00e9/notas.md");
    }

    @Test
    void neverOpensSpecialFiles() throws Exception {
        assumeTrue(mkfifo(root.resolve("pipe")), "mkfifo not available");
        Path outsidePipe = outside.resolve("pipe");
        assumeTrue(mkfifo(outsidePipe), "mkfifo not available");
        Files.createSymbolicLink(root.resolve("pipe-link"), outsidePipe);

        ScanResult result = scan(DEFAULTS, LIMITS);

        assertThat(result.exclusions())
                .extracting(ExclusionGroup::reason, ExclusionGroup::examples)
                .containsExactly(
                        tuple(ExclusionReason.SYMLINK, List.of("pipe-link")),
                        tuple(ExclusionReason.SPECIAL_FILE, List.of("pipe")));
        assertThat(sampled).isEmpty();
    }

    @Test
    void reportsUnreadableFilesAndDirectories() throws IOException {
        assumeTrue(isPosix(), "POSIX permissions not supported");
        Path file = write("locked.txt", "x");
        Path dir = Files.createDirectory(root.resolve("locked-dir"));
        write("locked-dir/inner.txt", "x");
        for (Path path : List.of(file, dir)) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("---------"));
            locked.add(path);
        }
        assumeTrue(!Files.isReadable(file), "running with privileges that ignore modes");

        ScanResult result = scan(DEFAULTS, LIMITS);

        assertThat(result.totals().unreadableFiles()).isEqualTo(1);
        assertThat(result.totals().excludedDirectories()).isEqualTo(1);
        assertThat(result.exclusions())
                .containsExactly(
                        new ExclusionGroup(
                                ExclusionReason.UNREADABLE,
                                null,
                                null,
                                1,
                                1,
                                List.of("locked-dir", "locked.txt")));
    }

    @Test
    void fileRemovedDuringScanIsUnreadableAndScanContinues() throws IOException {
        write("gone.txt", "x");
        write("kept.txt", "y");
        SafeScanner scanner =
                new SafeScanner(
                        (file, max) -> {
                            if (file.getFileName().toString().equals("gone.txt")) {
                                Files.delete(file);
                                throw new NoSuchFileException("gone");
                            }
                            return Files.readAllBytes(file);
                        });

        ScanResult result = scanner.scan(root, DEFAULTS, LIMITS);

        assertThat(result.totals().includedFiles()).isEqualTo(1);
        assertThat(result.totals().unreadableFiles()).isEqualTo(1);
    }

    @Test
    void appliesDepthLimit() throws IOException {
        write("a/b/ok.txt", "x");
        write("a/b/c/deep.txt", "x");
        write("a/b/c.txt", "x");

        ScanResult result = scan(DEFAULTS, new SafeScanner.Limits(1000, 2, 1024));

        assertThat(result.exclusions())
                .containsExactly(
                        new ExclusionGroup(
                                ExclusionReason.DEPTH_LIMIT,
                                null,
                                null,
                                2,
                                1,
                                List.of("a/b/c", "a/b/c.txt", "a/b/ok.txt")));
    }

    @Test
    void stopsAtEntryLimit() throws IOException {
        for (int i = 0; i < 10; i++) {
            write("f" + i + ".txt", "x");
        }

        ScanResult result = scan(DEFAULTS, new SafeScanner.Limits(4, 10, 1024));

        assertThat(result.truncated()).isTrue();
        assertThat(result.totals().files()).isEqualTo(4);
    }

    @Test
    void appliesCustomPatternsAfterDefaults() throws IOException {
        write("docs/generated/a.md", "x");
        write("app.log", "x");
        write(".env", "x");

        ScanResult result =
                scan(
                        new SafeScanner.Rules(
                                DefaultExclusionRules.ALL,
                                List.of(
                                        PathPattern.compile("docs/generated/"),
                                        PathPattern.compile("*.log"),
                                        PathPattern.compile(".env"))),
                        LIMITS);

        assertThat(result.exclusions())
                .extracting(ExclusionGroup::reason, ExclusionGroup::ruleId, ExclusionGroup::pattern)
                .containsExactly(
                        tuple(ExclusionReason.DEFAULT_RULE, "env-files", null),
                        tuple(ExclusionReason.CUSTOM_PATTERN, null, "*.log"),
                        tuple(ExclusionReason.CUSTOM_PATTERN, null, "docs/generated/"));
    }

    @Test
    void keepsTheFirstExamplesInOrder() throws IOException {
        for (int i = 0; i < 30; i++) {
            write("logs/f%02d.png".formatted(i), "x");
        }

        ExclusionGroup group = scan(DEFAULTS, LIMITS).exclusions().getFirst();

        assertThat(group.files()).isEqualTo(30);
        assertThat(group.examples())
                .hasSize(SafeScanner.EXAMPLES)
                .startsWith("logs/f00.png")
                .endsWith("logs/f19.png");
    }

    @Test
    void reportsOnlyIncludedFilesToTheListener() throws IOException {
        write("src/Main.java", "class Main {}");
        write("README.md", "# X");
        write(".env", "SECRET=1");
        write("node_modules/lib/index.js", "module.exports = 1");
        write("big.txt", "x".repeat(2000));
        Files.write(root.resolve("data.bin"), new byte[] {1, 0, 2});
        Files.createSymbolicLink(root.resolve("link.txt"), outside.resolve("secret.txt"));
        List<String> reported = new ArrayList<>();

        ScanResult result =
                new SafeScanner()
                        .scan(
                                root,
                                DEFAULTS,
                                LIMITS,
                                (relativePath, file, sizeBytes, language) -> {
                                    assertThat(file).isEqualTo(root.resolve(relativePath));
                                    reported.add(relativePath + ":" + sizeBytes + ":" + language);
                                });

        assertThat(reported)
                .containsExactlyInAnyOrder("src/Main.java:13:java", "README.md:3:markdown");
        assertThat(result.totals().includedFiles()).isEqualTo(reported.size());
    }

    private ScanResult scan(SafeScanner.Rules rules, SafeScanner.Limits limits) throws IOException {
        SafeScanner scanner =
                new SafeScanner(
                        (file, max) -> {
                            sampled.add(file);
                            return Files.readAllBytes(file);
                        });
        return scanner.scan(root, rules, limits);
    }

    private Path write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static boolean isPosix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    // Test fixture only: creates a FIFO with the system tool; nothing imported is executed.
    private static boolean mkfifo(Path path) {
        try {
            return new ProcessBuilder("mkfifo", path.toString()).start().waitFor() == 0
                    && Files.exists(path);
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
