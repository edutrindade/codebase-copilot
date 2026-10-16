package com.codebasecopilot.shared.localroots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalRootsTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(LocalRootsConfiguration.class);

    @TempDir Path tempDir;

    @Test
    void emptyShortcutCreatesNoRoot() {
        runner.withPropertyValues("app.projects-root=")
                .run(context -> assertThat(context.getBean(LocalRoots.class).isEmpty()).isTrue());
    }

    @Test
    void shortcutCreatesDefaultRootWithDefaultLabel() {
        runner.withPropertyValues("app.projects-root=" + tempDir)
                .run(
                        context ->
                                assertThat(context.getBean(LocalRoots.class).list())
                                        .containsExactly(
                                                new LocalRoot("default", "Projetos", tempDir)));
    }

    @Test
    void shortcutHonorsLabel() {
        runner.withPropertyValues(
                        "app.projects-root=" + tempDir, "app.projects-root-label=Meus códigos")
                .run(
                        context ->
                                assertThat(context.getBean(LocalRoots.class).find("default"))
                                        .map(LocalRoot::label)
                                        .contains("Meus códigos"));
    }

    @Test
    void listReplacesShortcutAndDefaultsLabelToKey() throws IOException {
        Path work = Files.createDirectory(tempDir.resolve("work"));
        Path oss = Files.createDirectory(tempDir.resolve("oss"));

        runner.withPropertyValues(
                        "app.projects-root=" + tempDir,
                        "app.local-roots[0].key=work",
                        "app.local-roots[0].label=Trabalho",
                        "app.local-roots[0].path=" + work,
                        "app.local-roots[1].key=open-source-2",
                        "app.local-roots[1].path=" + oss)
                .run(
                        context -> {
                            LocalRoots roots = context.getBean(LocalRoots.class);
                            assertThat(roots.list())
                                    .containsExactly(
                                            new LocalRoot("work", "Trabalho", work),
                                            new LocalRoot("open-source-2", "open-source-2", oss));
                            assertThat(roots.find("default")).isEmpty();
                        });
    }

    @Test
    void normalizesPath() {
        runner.withPropertyValues("app.projects-root=" + tempDir + "/./")
                .run(
                        context ->
                                assertThat(context.getBean(LocalRoots.class).find("default"))
                                        .map(LocalRoot::path)
                                        .contains(tempDir));
    }

    @Test
    void rejectsRelativePath() {
        assertFailsWith("path must be absolute", "app.projects-root=relative/dir");
    }

    @Test
    void rejectsMissingDirectory() {
        assertFailsWith(
                "path must be an existing directory",
                "app.projects-root=" + tempDir.resolve("missing"));
    }

    @Test
    void rejectsFile() throws IOException {
        Path file = Files.createFile(tempDir.resolve("file.txt"));

        assertFailsWith("path must be an existing directory", "app.projects-root=" + file);
    }

    @Test
    void rejectsUnreadableDirectory() throws IOException {
        assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX permissions not supported");
        Path locked = Files.createDirectory(tempDir.resolve("locked"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("-wx------"));
        try {
            assumeTrue(!Files.isReadable(locked), "running with privileges that ignore modes");
            assertFailsWith("path must be readable", "app.projects-root=" + locked);
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void rejectsEmptyPathInList() {
        assertFailsWith(
                "app.local-roots[0]: path is required",
                "app.local-roots[0].key=work",
                "app.local-roots[0].path=");
    }

    @Test
    void rejectsDuplicateKey() {
        assertFailsWith(
                "app.local-roots[1]: duplicate key 'work'",
                "app.local-roots[0].key=work",
                "app.local-roots[0].path=" + tempDir,
                "app.local-roots[1].key=work",
                "app.local-roots[1].path=" + tempDir);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Work", "my_root", "raiz.1", "ção"})
    void rejectsKeyOutsideFormat(String key) {
        assertFailsWith(
                "key must match [a-z0-9-]+",
                "app.local-roots[0].key=" + key,
                "app.local-roots[0].path=" + tempDir);
    }

    @Test
    void rejectsMissingKey() {
        assertFailsWith("key must match [a-z0-9-]+", "app.local-roots[0].path=" + tempDir);
    }

    private void assertFailsWith(String message, String... properties) {
        runner.withPropertyValues(properties)
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .isInstanceOf(IllegalStateException.class)
                                        .hasMessageContaining(message));
    }
}
