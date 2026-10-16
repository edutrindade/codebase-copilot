package com.codebasecopilot.importjob;

import static com.codebasecopilot.importjob.ZipFixtures.randomBytes;
import static com.codebasecopilot.importjob.ZipFixtures.raw;
import static com.codebasecopilot.importjob.ZipFixtures.zip;
import static com.codebasecopilot.importjob.ZipFixtures.zipOfBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.codebasecopilot.shared.fs.Directories;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Acceptance of Etapa 2: malicious archives are rejected and nothing is written outside the job.
class ZipExtractorTest {

    private static final ZipExtractor.Limits LIMITS =
            new ZipExtractor.Limits(10, 1024 * 1024, 256 * 1024, 100, 32 * 1024, 5, 100);

    private final ZipExtractor extractor = new ZipExtractor(LIMITS);

    @TempDir Path tempDir;

    private Path job;
    private Path target;
    private Path sentinel;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = tempDir.toRealPath();
        sentinel = Files.writeString(tempDir.resolve("sentinel.txt"), "intact");
        job = Files.createDirectories(tempDir.resolve("imports/job"));
        target = job.resolve("source");
    }

    @Test
    void extractsFilesAndDirectories() throws Exception {
        ZipExtractor.Result result =
                extract(zip("src/", "", "src/Main.java", "class Main {}", "README.md", "# X"));

        assertThat(result).isEqualTo(new ZipExtractor.Result(3, 16));
        assertThat(target.resolve("src/Main.java")).hasContent("class Main {}");
        assertThat(target.resolve("README.md")).hasContent("# X");
    }

    @Test
    void ignoresEmptyAndDotSegments() throws Exception {
        extract(raw().file("./a//b/./c.txt", "c").build());

        assertThat(target.resolve("a/b/c.txt")).hasContent("c");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../evil.txt",
                "a/../../evil.txt",
                "a/./../../evil.txt",
                "a/../evil.txt",
                "/etc/evil.txt",
                "C:/evil.txt",
                "C:\\evil.txt",
                "a\\..\\..\\evil.txt",
                "a\u0007evil.txt",
                "a\u0000evil.txt"
            })
    void rejectsUnsafePaths(String name) {
        assertRejected(raw().file(name, "evil").build(), ImportErrorCode.ZIP_UNSAFE_PATH);
    }

    @Test
    void rejectsTooLongPath() {
        assertRejected(raw().file("a".repeat(101), "x").build(), ImportErrorCode.ZIP_UNSAFE_PATH);
    }

    @Test
    void rejectsTooDeepPath() {
        assertRejected(raw().file("a/b/c/d/e/f.txt", "x").build(), ImportErrorCode.ZIP_TOO_DEEP);
    }

    @Test
    void rejectsRepeatedName() {
        assertRejected(
                raw().file("a.txt", "first").file("a.txt", "second").build(),
                ImportErrorCode.ZIP_DUPLICATE_ENTRY);
    }

    @Test
    void rejectsFileWhereDirectoryExists() {
        assertRejected(
                raw().file("a/b.txt", "x").file("a", "x").build(),
                ImportErrorCode.ZIP_DUPLICATE_ENTRY);
        assertRejected(
                raw().file("a", "x").file("a/b.txt", "x").build(),
                ImportErrorCode.ZIP_DUPLICATE_ENTRY);
    }

    @Test
    void rejectsCaseVariantsOnCaseInsensitiveFileSystem() throws IOException {
        Path probe = Files.writeString(job.resolve("probe"), "");
        boolean caseInsensitive = Files.exists(job.resolve("PROBE"));
        Files.delete(probe);
        assumeTrue(caseInsensitive, "file system is case-sensitive");

        assertRejected(
                raw().file("A.txt", "x").file("a.txt", "y").build(),
                ImportErrorCode.ZIP_DUPLICATE_ENTRY);
    }

    @Test
    void rejectsTooManyEntries() {
        ZipFixtures.Raw zip = raw();
        for (int i = 0; i <= LIMITS.maxEntries(); i++) {
            zip.file("f" + i + ".txt", "x");
        }

        assertRejected(zip.build(), ImportErrorCode.ZIP_TOO_MANY_ENTRIES);
    }

    @Test
    void rejectsHighlyCompressedEntry() {
        assertRejected(
                zipOfBytes("zeros.bin", new byte[200 * 1024]),
                ImportErrorCode.ZIP_COMPRESSION_RATIO_EXCEEDED);
    }

    @Test
    void rejectsEntryLargerThanDeclared() {
        byte[] data = randomBytes(300 * 1024, 1);

        assertRejected(
                raw().declaringSize("small.bin", data, 10).build(),
                ImportErrorCode.ZIP_ENTRY_TOO_LARGE);
    }

    @Test
    void rejectsTotalAboveLimitEvenWithValidEntries() {
        ZipFixtures.Raw zip = raw();
        for (int i = 0; i < 5; i++) {
            zip.file("part" + i + ".bin", randomBytes(250 * 1024, i));
        }

        assertRejected(zip.build(), ImportErrorCode.ZIP_UNCOMPRESSED_TOO_LARGE);
    }

    @Test
    void rejectsArchivesThatAreNotZip() {
        assertRejected("not a zip".getBytes(StandardCharsets.UTF_8), ImportErrorCode.ZIP_INVALID);
        byte[] valid = zip("a.txt", "content");
        assertRejected(Arrays.copyOf(valid, valid.length - 10), ImportErrorCode.ZIP_INVALID);
        assertRejected(raw().encrypted("secret.txt", "x").build(), ImportErrorCode.ZIP_INVALID);
    }

    @Test
    void extractsLinkEntryAsRegularFile() throws Exception {
        extract(raw().symlink("link", "/etc/passwd").file("a.txt", "a").build());

        assertThat(Files.isSymbolicLink(target.resolve("link"))).isFalse();
        assertThat(target.resolve("link")).isRegularFile().hasContent("/etc/passwd");
    }

    @Test
    void acceptsEmptyArchive() throws Exception {
        assertThat(extract(zip())).isEqualTo(new ZipExtractor.Result(0, 0));
        assertThat(target).isEmptyDirectory();
    }

    private ZipExtractor.Result extract(byte[] archive) throws Exception {
        Path file = Files.write(job.resolve("upload.zip"), archive);
        return extractor.extract(file, target);
    }

    private void assertRejected(byte[] archive, ImportErrorCode code) {
        ZipRejectedException rejection =
                catchThrowableOfType(ZipRejectedException.class, () -> extract(archive));

        assertThat(rejection).as("archive should be rejected").isNotNull();
        assertThat(rejection.code()).isEqualTo(code);
        assertThat(sentinel).hasContent("intact");
        assertThat(filesOutsideJob()).containsExactly(sentinel);
        cleanJob();
    }

    private List<Path> filesOutsideJob() {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            return paths.filter(p -> !p.startsWith(job) && Files.isRegularFile(p)).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // The worker deletes the job folder; here it is reset so one test can check several archives.
    private void cleanJob() {
        try {
            Directories.deleteRecursively(job);
            Files.createDirectories(job);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
