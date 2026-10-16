package com.codebasecopilot.shared.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.codebasecopilot.shared.fs.ConfinedPaths.Resolution;
import com.codebasecopilot.shared.fs.ConfinedPaths.Status;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfinedPathsTest {

    @TempDir Path tempDir;

    private Path base;
    private Path outside;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = tempDir.toRealPath();
        base = Files.createDirectories(tempDir.resolve("base"));
        Files.createDirectories(base.resolve("a/b"));
        Files.createFile(base.resolve("file.txt"));
        outside = Files.createDirectory(tempDir.resolve("outside"));
    }

    @Test
    void resolvesBaseItself() {
        assertThat(ConfinedPaths.resolveDirectory(base, ""))
                .isEqualTo(new Resolution(Status.FOUND, base));
    }

    @Test
    void resolvesNestedDirectory() {
        assertThat(ConfinedPaths.resolveDirectory(base, "a/b"))
                .isEqualTo(new Resolution(Status.FOUND, base.resolve("a/b")));
    }

    @Test
    void resolvesToRealPathWhenBaseIsLink() throws IOException {
        Path link = Files.createSymbolicLink(tempDir.resolve("base-link"), base);

        assertThat(ConfinedPaths.resolveDirectory(link, "a"))
                .isEqualTo(new Resolution(Status.FOUND, base.resolve("a")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "a/missing", "a/b/missing/c"})
    void reportsMissingDirectory(String path) {
        assertThat(status(path)).isEqualTo(Status.NOT_FOUND);
    }

    @Test
    void reportsMissingBase() {
        assertThat(ConfinedPaths.resolveDirectory(tempDir.resolve("gone"), "").status())
                .isEqualTo(Status.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file.txt", "file.txt/x"})
    void reportsFile(String path) {
        assertThat(status(path)).isEqualTo(Status.NOT_A_DIRECTORY);
    }

    @Test
    void neverFollowsLinks() throws IOException {
        Files.createSymbolicLink(base.resolve("inside-link"), base.resolve("a"));
        Files.createSymbolicLink(base.resolve("a/outside-link"), outside);
        Files.createSymbolicLink(base.resolve("file-link"), base.resolve("file.txt"));
        Files.createSymbolicLink(base.resolve("dangling-link"), tempDir.resolve("nowhere"));

        assertThat(status("inside-link")).isEqualTo(Status.SYMLINK);
        assertThat(status("inside-link/b")).isEqualTo(Status.SYMLINK);
        assertThat(status("a/outside-link")).isEqualTo(Status.SYMLINK);
        assertThat(status("file-link")).isEqualTo(Status.SYMLINK);
        assertThat(status("dangling-link")).isEqualTo(Status.SYMLINK);
    }

    @Test
    void reportsUnreadableDirectory() throws IOException {
        assumePosix();
        Path locked = Files.createDirectory(base.resolve("locked"));
        Path unsearchable = Files.createDirectories(base.resolve("unsearchable/child"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("-wx------"));
        Files.setPosixFilePermissions(
                unsearchable.getParent(), PosixFilePermissions.fromString("r--------"));
        try {
            assumeTrue(!Files.isReadable(locked), "running with privileges that ignore modes");
            assertThat(status("locked")).isEqualTo(Status.NOT_READABLE);
            assertThat(status("unsearchable/child")).isEqualTo(Status.NOT_READABLE);
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
            Files.setPosixFilePermissions(
                    unsearchable.getParent(), PosixFilePermissions.fromString("rwx------"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"a/../b", "./a", "a//b", ".."})
    void requiresNormalizedPath(String path) {
        assertThatThrownBy(() -> ConfinedPaths.resolveDirectory(base, path))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Status status(String path) {
        return ConfinedPaths.resolveDirectory(base, path).status();
    }

    private static void assumePosix() {
        assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX permissions not supported");
    }
}
