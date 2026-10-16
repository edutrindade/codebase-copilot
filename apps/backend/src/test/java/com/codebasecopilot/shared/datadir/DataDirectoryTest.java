package com.codebasecopilot.shared.datadir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.codebasecopilot.shared.localroots.LocalRoots;
import com.codebasecopilot.shared.localroots.LocalRootsProperties;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DataDirectoryTest {

    @TempDir Path tempDir;

    private Path home;
    private Path workingDir;
    private Path root;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = tempDir.toRealPath();
        home = Files.createDirectory(tempDir.resolve("home"));
        workingDir = Files.createDirectory(tempDir.resolve("cwd"));
        root = Files.createDirectory(tempDir.resolve("root"));
    }

    @Test
    void blankUsesDefaultUnderHome() {
        assertThat(create(null).importsDir()).isEqualTo(home.resolve(".codebase-copilot/imports"));
        assertThat(create("  ").importsDir()).isEqualTo(home.resolve(".codebase-copilot/imports"));
        assertThat(home.resolve(".codebase-copilot/imports")).isDirectory();
    }

    @Test
    void expandsTilde() {
        assertThat(create("~").importsDir()).isEqualTo(home.resolve("imports"));
        assertThat(create("~/data").importsDir()).isEqualTo(home.resolve("data/imports"));
    }

    @Test
    void createsMissingDirectories() {
        DataDirectory dataDir = create(tempDir.resolve("a/b/c").toString());

        assertThat(dataDir.importsDir()).isEqualTo(tempDir.resolve("a/b/c/imports")).isDirectory();
    }

    @Test
    void resolvesImportDirById() {
        UUID id = UUID.randomUUID();

        assertThat(create(tempDir.resolve("data").toString()).importDir(id))
                .isEqualTo(tempDir.resolve("data/imports/" + id));
    }

    @Test
    void rejectsTildeUser() {
        assertInvalid("'~user' is not supported", "~other/data");
    }

    @Test
    void rejectsRelativePath() {
        assertInvalid("must be absolute", "data");
    }

    @Test
    void rejectsFile() throws IOException {
        Path file = Files.createFile(tempDir.resolve("file.txt"));

        assertInvalid("must be a directory", file.toString());
    }

    @Test
    void rejectsReadOnlyDirectory() throws IOException {
        assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX permissions not supported");
        Path locked = Files.createDirectory(tempDir.resolve("locked"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-x------"));
        try {
            assumeTrue(!Files.isWritable(locked), "running with privileges that ignore modes");
            assertInvalid("must be readable and writable", locked.toString());
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void rejectsLocalRoot() {
        assertInvalid("must not be inside local root 'work'", root.toString());
    }

    @Test
    void rejectsDirectoryInsideRootWithoutCreatingIt() {
        assertInvalid("must not be inside local root 'work'", root.resolve("data").toString());
        assertThat(root.resolve("data")).doesNotExist();
    }

    @Test
    void rejectsDirectoryContainingRoot() {
        assertInvalid("must not be inside local root 'work'", tempDir.toString());
    }

    @Test
    void rejectsRootReachedThroughLink() throws IOException {
        Path link = Files.createSymbolicLink(tempDir.resolve("link"), root);

        assertInvalid("must not be inside local root 'work'", link.resolve("data").toString());
        assertThatThrownBy(() -> create(root.resolve("data").toString(), link))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be inside local root 'work'");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsDirectoryInsideRepository(boolean gitIsDirectory) throws IOException {
        Path repository = Files.createDirectory(tempDir.resolve("repo"));
        if (gitIsDirectory) {
            Files.createDirectory(repository.resolve(".git"));
        } else {
            Files.writeString(repository.resolve(".git"), "gitdir: elsewhere");
        }
        workingDir = Files.createDirectories(repository.resolve("apps/backend"));

        assertInvalid("must be outside the Codebase Copilot repository", repository + "/data");
        assertThat(create(tempDir.resolve("outside").toString()).importsDir()).isDirectory();
    }

    @Test
    void failsAtStartupWhenInvalid() {
        runner().withPropertyValues("app.data-dir=" + root.resolve("data"))
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .isInstanceOf(IllegalStateException.class)
                                        .hasMessageContaining("must not be inside local root"));
    }

    @Test
    void startsWithValidDirectory() {
        runner().withPropertyValues("app.data-dir=" + tempDir.resolve("data"))
                .run(
                        context ->
                                assertThat(context.getBean(DataDirectory.class).importsDir())
                                        .isEqualTo(tempDir.resolve("data/imports"))
                                        .isDirectory());
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(DataDirectoryConfiguration.class)
                .withBean(LocalRoots.class, () -> roots(root));
    }

    private DataDirectory create(String value) {
        return create(value, root);
    }

    private DataDirectory create(String value, Path rootPath) {
        return DataDirectory.from(value, roots(rootPath), home, workingDir);
    }

    private static LocalRoots roots(Path rootPath) {
        return LocalRoots.from(
                new LocalRootsProperties(
                        null,
                        null,
                        List.of(
                                new LocalRootsProperties.Entry(
                                        "work", null, rootPath.toString()))));
    }

    private void assertInvalid(String message, String value) {
        assertThatThrownBy(() -> create(value))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }
}
