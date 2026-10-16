package com.codebasecopilot.project;

import static com.codebasecopilot.ImportJobRows.insertJob;
import static com.codebasecopilot.ImportJobRows.insertProjectJob;
import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.ImportJobRows.setCurrentImport;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.shared.datadir.DataDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// Import jobs are inserted with SQL until the upload exists (F3).
@IntegrationTest
class ProjectImportStateApiIT {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private DataDirectory dataDirectory;
    @Autowired private ProjectService service;

    private UUID workspaceId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        workspaceId = insertWorkspace(jdbc);
        projectId = insertZipProject(jdbc, workspaceId);
    }

    @Test
    void deleteIsRefusedWhileImportIsActive() {
        insertJob(jdbc, projectId, "EXTRACTING");

        assertThat(mvc.delete().uri("/api/v1/projects/" + projectId))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"code": "PROJECT_BUSY", "title": "Projeto ocupado", "status": 409}
                        """);
        assertThat(mvc.get().uri("/api/v1/projects/" + projectId)).hasStatusOk();
    }

    @Test
    void deleteIsRefusedWhileAnyJobIsActive() {
        insertProjectJob(jdbc, projectId, "INDEX", true);

        assertThat(mvc.delete().uri("/api/v1/projects/" + projectId))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("PROJECT_BUSY");
        assertThat(mvc.get().uri("/api/v1/projects/" + projectId)).hasStatusOk();
    }

    @Test
    void deleteRemovesImportsAndTheirFolders() throws IOException {
        UUID current = insertJob(jdbc, projectId, "COMPLETED");
        UUID failed = insertJob(jdbc, projectId, "FAILED");
        setCurrentImport(jdbc, projectId, current);
        Path currentDir = folderWithFile(current);
        Path failedDir = folderWithFile(failed);
        UUID otherJob = insertJob(jdbc, insertZipProject(jdbc, workspaceId), "COMPLETED");
        Path otherDir = folderWithFile(otherJob);

        assertThat(mvc.delete().uri("/api/v1/projects/" + projectId))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(
                        jdbc.sql("SELECT count(*) FROM import_jobs WHERE project_id = :id")
                                .param("id", projectId)
                                .query(Long.class)
                                .single())
                .isZero();
        assertThat(currentDir).doesNotExist();
        assertThat(failedDir).doesNotExist();
        assertThat(otherDir).isDirectory();
    }

    @Test
    void deleteWithoutImportFoldersSucceeds() {
        insertJob(jdbc, projectId, "REJECTED");

        assertThat(mvc.delete().uri("/api/v1/projects/" + projectId))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void zipProjectShowsCurrentImport() {
        UUID current = insertJob(jdbc, projectId, "COMPLETED");
        setCurrentImport(jdbc, projectId, current);
        insertJob(jdbc, projectId, "PENDING");

        assertThat(mvc.get().uri("/api/v1/projects/" + projectId))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.source")
                .isEqualTo(
                        Map.of(
                                "type",
                                "ZIP",
                                "currentImport",
                                Map.of("id", current.toString(), "status", "COMPLETED")));
    }

    @Test
    void listingShowsCurrentImportOfEachProject() {
        UUID current = insertJob(jdbc, projectId, "COMPLETED");
        setCurrentImport(jdbc, projectId, current);
        UUID withoutImport = insertZipProject(jdbc, workspaceId);

        assertThat(mvc.get().uri("/api/v1/workspaces/" + workspaceId + "/projects"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(
                        json -> {
                            assertThat(json)
                                    .extractingPath(
                                            "$.items[?(@.id == '%s')].source.currentImport.id"
                                                    .formatted(projectId))
                                    .isEqualTo(List.of(current.toString()));
                            assertThat(json)
                                    .extractingPath(
                                            "$.items[?(@.id == '%s')].source.currentImport"
                                                    .formatted(withoutImport))
                                    .isEqualTo(Collections.singletonList(null));
                        });
    }

    @Test
    void replaceCurrentImportReturnsThePreviousOne() {
        UUID first = insertJob(jdbc, projectId, "COMPLETED");
        UUID second = insertJob(jdbc, projectId, "COMPLETED");

        assertThat(service.replaceCurrentImport(projectId, first)).isNull();
        assertThat(service.replaceCurrentImport(projectId, second)).isEqualTo(first);
        assertThat(service.get(projectId).currentImport())
                .isEqualTo(new ProjectImports.CurrentImport(second, "COMPLETED"));
    }

    private Path folderWithFile(UUID importJobId) throws IOException {
        Path dir = Files.createDirectories(dataDirectory.importDir(importJobId).resolve("source"));
        Files.writeString(dir.resolve("file.txt"), "text");
        return dataDirectory.importDir(importJobId);
    }
}
