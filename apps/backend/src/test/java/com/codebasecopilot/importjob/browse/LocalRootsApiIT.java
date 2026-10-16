package com.codebasecopilot.importjob.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.TestFileSystemInitializer;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Tree described in TestFileSystemInitializer.
@IntegrationTest
class LocalRootsApiIT {

    private static final Path DEFAULT_ROOT = TestFileSystemInitializer.ROOTS_DIR.resolve("default");

    @Autowired private MockMvcTester mvc;

    @Test
    void listsConfiguredRootsWithoutPaths() throws UnsupportedEncodingException {
        MvcTestResult result = mvc.get().uri("/api/v1/local-roots").exchange();

        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo(
                        """
                        [
                          {"key": "default", "label": "Projetos"},
                          {"key": "work", "label": "Trabalho"}
                        ]
                        """);
        assertNoAbsolutePath(result);
    }

    @Test
    void listsRealSubdirectoriesOfRoot() throws UnsupportedEncodingException {
        Path locked = DEFAULT_ROOT.resolve("locked");
        boolean hasLocked = Files.exists(locked);
        MvcTestResult result = directories("default", null);

        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .satisfies(
                        json -> {
                            assertThat(json).extractingPath("$.path").isEqualTo("");
                            assertThat(json).extractingPath("$.truncated").isEqualTo(false);
                            assertThat(json)
                                    .extractingPath("$.directories[*].name")
                                    .isEqualTo(
                                            hasLocked
                                                    ? List.of(
                                                            "alpha", "Beta", "gamma", "locked",
                                                            "many")
                                                    : List.of("alpha", "Beta", "gamma", "many"));
                            assertThat(json)
                                    .extractingPath("$.directories[*].hasChildren")
                                    .isEqualTo(
                                            hasLocked
                                                    ? List.of(true, false, false, false, true)
                                                    : List.of(true, false, false, true));
                            assertThat(json)
                                    .extractingPath("$.directories[*].readable")
                                    .isEqualTo(
                                            hasLocked
                                                    ? List.of(
                                                            true,
                                                            true,
                                                            true,
                                                            Files.isReadable(locked),
                                                            true)
                                                    : List.of(true, true, true, true));
                            assertThat(json)
                                    .extractingPath("$.directories[0].relativePath")
                                    .isEqualTo("alpha");
                        });
        assertNoAbsolutePath(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alpha", "alpha/", "./alpha", "alpha//"})
    void normalizesPathAndBuildsRelativePaths(String path) {
        assertThat(directories("default", path))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo(
                        """
                        {
                          "path": "alpha",
                          "directories": [
                            {"name": "inner", "relativePath": "alpha/inner", "hasChildren": false, "readable": true}
                          ],
                          "truncated": false
                        }
                        """);
    }

    @Test
    void emptyPathIsTheRoot() {
        assertThat(directories("default", ""))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.path")
                .isEqualTo("");
    }

    @Test
    void truncatesAboveLimit() {
        assertThat(directories("default", "many"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(
                        json -> {
                            assertThat(json)
                                    .extractingPath("$.directories[*].name")
                                    .isEqualTo(List.of("d1", "d2", "d3", "d4", "d5"));
                            assertThat(json).extractingPath("$.truncated").isEqualTo(true);
                        });
    }

    @Test
    void reportsUnreadableDirectory() {
        Path locked = DEFAULT_ROOT.resolve("locked");
        assumeTrue(Files.exists(locked), "POSIX permissions not supported");
        assumeTrue(!Files.isReadable(locked), "running with privileges that ignore modes");

        assertCode(directories("default", "locked"), HttpStatus.CONFLICT, "DIRECTORY_NOT_READABLE");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing",
                "alpha/missing",
                "readme.txt",
                "outside-link",
                "outside-link/secret",
                "gamma/link-to-alpha"
            })
    void missingFilesAndLinksAreNotFound(String path) {
        assertCode(directories("default", path), HttpStatus.NOT_FOUND, "DIRECTORY_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside", "alpha/../../outside", "/etc", "//"})
    void rejectsPathsOutsideRoot(String path) {
        assertCode(directories("default", path), HttpStatus.BAD_REQUEST, "PATH_OUTSIDE_ROOT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\\b", "a\u0000b", "a\tb"})
    void rejectsForbiddenCharacters(String path) {
        assertThat(directories("default", path))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .satisfies(
                        json -> {
                            assertThat(json)
                                    .extractingPath("$.code")
                                    .isEqualTo("VALIDATION_FAILED");
                            assertThat(json).extractingPath("$.errors[0].field").isEqualTo("path");
                        });
    }

    @Test
    void unknownRootIsNotFound() {
        assertCode(directories("nope", null), HttpStatus.NOT_FOUND, "ROOT_NOT_FOUND");
    }

    @Test
    void invalidCharactersWinOverUnknownRoot() {
        assertCode(directories("nope", "a\\b"), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    @Test
    void unknownRootWinsOverPathOutsideRoot() {
        assertCode(directories("nope", "../x"), HttpStatus.NOT_FOUND, "ROOT_NOT_FOUND");
    }

    @Test
    void pathOutsideRootWinsOverMissingDirectory() {
        assertCode(
                directories("default", "../missing"), HttpStatus.BAD_REQUEST, "PATH_OUTSIDE_ROOT");
    }

    @Test
    void errorsDoNotExposeAbsolutePaths() throws UnsupportedEncodingException {
        assertNoAbsolutePath(directories("default", "outside-link"));
        assertNoAbsolutePath(directories("default", "missing"));
    }

    private MvcTestResult directories(String rootKey, String path) {
        var request = mvc.get().uri("/api/v1/local-roots/{rootKey}/directories", rootKey);
        return (path == null ? request : request.param("path", path)).exchange();
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static void assertNoAbsolutePath(MvcTestResult result)
            throws UnsupportedEncodingException {
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(TestFileSystemInitializer.ROOTS_DIR.toString())
                .doesNotContain("test-roots");
    }
}
