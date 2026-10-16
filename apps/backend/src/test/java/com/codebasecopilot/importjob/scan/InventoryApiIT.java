package com.codebasecopilot.importjob.scan;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.TestFileSystemInitializer;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Trees described in TestFileSystemInitializer.
@IntegrationTest
class InventoryApiIT {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        workspaceId = insertWorkspace(jdbc);
    }

    @Test
    void inventoriesLocalFolder() throws UnsupportedEncodingException {
        String projectId = localProject("work", "inventory-repo");

        MvcTestResult result = inventory(projectId);

        assertThat(result)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {
                          "projectId": "%s",
                          "settingsVersion": 0,
                          "truncated": false,
                          "totals": {
                            "files": 8, "includedFiles": 4, "includedBytes": 39,
                            "excludedFiles": 3, "unsupportedFiles": 1, "unreadableFiles": 0,
                            "excludedDirectories": 1
                          },
                          "byLanguage": [
                            {"language": "java", "files": 1, "bytes": 13},
                            {"language": "kotlin", "files": 1, "bytes": 13},
                            {"language": "markdown", "files": 1, "bytes": 6},
                            {"language": "text", "files": 1, "bytes": 7}
                          ],
                          "byDirectory": [
                            {"path": "", "includedFiles": 2, "excludedFiles": 3,
                             "unsupportedFiles": 1, "unreadableFiles": 0},
                            {"path": "src", "includedFiles": 2, "excludedFiles": 0,
                             "unsupportedFiles": 0, "unreadableFiles": 0}
                          ],
                          "exclusions": [
                            {"reason": "DEFAULT_RULE", "ruleId": "env-files", "pattern": null,
                             "files": 1, "directories": 0, "examples": [".env"]},
                            {"reason": "DEFAULT_RULE", "ruleId": "node-modules", "pattern": null,
                             "files": 0, "directories": 1, "examples": ["node_modules"]},
                            {"reason": "SYMLINK", "ruleId": null, "pattern": null,
                             "files": 1, "directories": 0, "examples": ["link-out"]},
                            {"reason": "TOO_LARGE", "ruleId": null, "pattern": null,
                             "files": 1, "directories": 0, "examples": ["big.txt"]},
                            {"reason": "BINARY", "ruleId": null, "pattern": null,
                             "files": 1, "directories": 0, "examples": ["data.bin"]}
                          ],
                          "warnings": []
                        }
                        """
                                .formatted(projectId));
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(TestFileSystemInitializer.ROOTS_DIR.toString())
                .doesNotContain("test-roots");
    }

    @Test
    void inventoriesCurrentZipImport() {
        String projectId = insertZipProject(jdbc, workspaceId).toString();
        MvcTestResult upload =
                mvc.post()
                        .uri("/api/v1/projects/{id}/imports/zip", projectId)
                        .multipart()
                        .file(
                                new MockMultipartFile(
                                        "file",
                                        "repo.zip",
                                        "application/zip",
                                        zip(
                                                "src/Main.java",
                                                "class Main {}",
                                                ".env",
                                                "S=1",
                                                "README.md",
                                                "# R")))
                        .exchange();
        String importId = read(upload, "$.id");
        await().atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                read(
                                        mvc.get().uri("/api/v1/imports/" + importId).exchange(),
                                        "$.status"),
                        "COMPLETED"::equals);

        assertThat(inventory(projectId))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {
                          "totals": {"files": 3, "includedFiles": 2, "excludedFiles": 1},
                          "exclusions": [{"reason": "DEFAULT_RULE", "ruleId": "env-files"}],
                          "warnings": []
                        }
                        """);
    }

    @Test
    void zipProjectWithoutImportIsNotImported() {
        assertCode(
                inventory(insertZipProject(jdbc, workspaceId).toString()), "SOURCE_NOT_IMPORTED");
    }

    @Test
    void missingFolderIsNotFound() {
        assertCode(inventory(localProject("work", "missing")), "SOURCE_DIRECTORY_NOT_FOUND");
    }

    @Test
    void pathThroughLinkIsRefused() {
        assertCode(inventory(localProject("default", "outside-link")), "SOURCE_IS_SYMLINK");
        assertCode(inventory(localProject("work", "inventory-repo/link-out")), "SOURCE_IS_SYMLINK");
    }

    @Test
    void unreadableFolderIsRefused() {
        Path locked = TestFileSystemInitializer.ROOTS_DIR.resolve("default/locked");
        assumeTrue(Files.exists(locked), "POSIX permissions not supported");
        assumeTrue(!Files.isReadable(locked), "running with privileges that ignore modes");

        assertCode(inventory(localProject("default", "locked")), "SOURCE_NOT_READABLE");
    }

    @Test
    void rootRemovedFromConfigurationIsReported() {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO projects (id, workspace_id, name, source_type,
                                              source_root_key, source_relative_path,
                                              created_at, updated_at)
                        VALUES (:id, :workspaceId, 'P', 'LOCAL_FOLDER', 'gone', 'a', now(),
                                now())
                        """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .update();

        assertCode(inventory(id.toString()), "SOURCE_ROOT_NOT_CONFIGURED");
    }

    @Test
    void unknownProjectIsNotFound() {
        assertThat(inventory(UUID.randomUUID().toString()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("PROJECT_NOT_FOUND");
    }

    @Test
    void warnsAboutCaseVariantPointingToSameFolder() {
        String project = localProject("work", "inventory-repo");
        String variant = localProject("work", "Inventory-Repo");
        boolean caseInsensitive =
                Files.exists(TestFileSystemInitializer.ROOTS_DIR.resolve("work/INVENTORY-REPO"));

        MvcTestResult result = inventory(project);

        if (caseInsensitive) {
            assertThat(result)
                    .bodyJson()
                    .isLenientlyEqualTo(
                            """
                            {"warnings": [{"code": "CASE_VARIANT_PROJECT", "relatedProjectIds": ["%s"]}]}
                            """
                                    .formatted(variant));
            assertThat(read(result, "$.warnings[0].message")).startsWith("Outro projeto");
        } else {
            assertThat(result).bodyJson().extractingPath("$.warnings").isEqualTo(List.of());
        }
    }

    @Test
    void warnsWhenNothingIsIncluded() {
        assertThat(inventory(localProject("work", "only-excluded")))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.warnings[*].code")
                .isEqualTo(List.of("NO_INCLUDED_FILES"));
    }

    private String localProject(String rootKey, String relativePath) {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/workspaces/{id}/projects", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name": "P", "sourceType": "LOCAL_FOLDER", "rootKey": "%s",
                                 "relativePath": "%s"}
                                """
                                        .formatted(rootKey, relativePath))
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private MvcTestResult inventory(String projectId) {
        return mvc.get().uri("/api/v1/projects/{id}/inventory", projectId).exchange();
    }

    private static void assertCode(MvcTestResult result, String code) {
        assertThat(result)
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo(code);
    }

    private static byte[] zip(String... namesAndContents) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                out.putNextEntry(new ZipEntry(namesAndContents[i]));
                out.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
