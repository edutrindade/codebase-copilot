package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.IndexRows.insertIndexJob;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static com.codebasecopilot.indexing.IndexingTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.jayway.jsonpath.DocumentContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Index state in GET /projects/{id} and in the project list (D-044).
@IntegrationTest
class ProjectIndexStateApiIT {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

    private IndexingTestClient client;
    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        client = new IndexingTestClient(mvc);
        workspaceId = insertWorkspace(jdbc);
    }

    @Test
    void projectWithoutIndexingHasNoState() {
        String projectId = client.localProject(workspaceId, "work", "inventory-repo");

        assertThat(mvc.get().uri("/api/v1/projects/{id}", projectId))
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"activeSnapshot": null, "indexing": null, "outdatedReasons": []}
                        """);
    }

    @Test
    void activeSnapshotFollowsIndexingAndSettings() {
        String projectId = client.localProject(workspaceId, "work", "inventory-repo");
        String first = client.index(projectId);

        DocumentContext project = project(projectId);
        assertThat((String) project.read("$.activeSnapshot.id")).isEqualTo(first);
        assertThat((String) project.read("$.activeSnapshot.status")).isEqualTo("READY");
        assertThat((Integer) project.read("$.activeSnapshot.counts.files")).isEqualTo(4);
        assertThat((Object) project.read("$.indexing")).isNull();
        assertThat(reasons(projectId)).isEmpty();

        client.putSettings(projectId, "{\"excludePatterns\": [\"*.md\"]}");
        assertThat(reasons(projectId)).containsExactly("SETTINGS_CHANGED");

        String second = client.index(projectId);
        assertThat((String) project(projectId).read("$.activeSnapshot.id")).isEqualTo(second);
        assertThat(reasons(projectId)).isEmpty();

        jdbc.sql("UPDATE snapshots SET chunking_version = 'lines-v0' WHERE id = :id")
                .param("id", UUID.fromString(second))
                .update();
        assertThat(reasons(projectId)).containsExactly("CHUNKING_CHANGED");
    }

    @Test
    void newZipImportMakesTheSnapshotOutdated() {
        UUID projectId = insertZipProject(jdbc, workspaceId);
        upload(projectId, "a.txt", "1");
        client.index(projectId.toString());
        assertThat(reasons(projectId.toString())).isEmpty();

        upload(projectId, "a.txt", "2");

        assertThat(reasons(projectId.toString())).containsExactly("SOURCE_REIMPORTED");
    }

    @Test
    void runningJobAppearsInProjectAndList() {
        UUID projectId = insertZipProject(jdbc, workspaceId);
        UUID jobId =
                insertIndexJob(
                        jdbc, projectId, insertSnapshot(jdbc, projectId, "BUILDING"), "RUNNING");
        jdbc.sql(
                        """
                        UPDATE index_jobs
                        SET stage = 'PROCESSING_FILES', progress_total = 10, progress_completed = 4
                        WHERE id = :id
                        """)
                .param("id", jobId)
                .update();
        String expected =
                """
                {"id": "%s", "status": "RUNNING", "stage": "PROCESSING_FILES",
                 "progress": {"total": 10, "completed": 4}}
                """
                        .formatted(jobId);

        assertThat(mvc.get().uri("/api/v1/projects/{id}", projectId))
                .bodyJson()
                .isLenientlyEqualTo("{\"indexing\": %s}".formatted(expected));
        assertThat(mvc.get().uri("/api/v1/workspaces/{id}/projects", workspaceId))
                .bodyJson()
                .isLenientlyEqualTo("{\"items\": [{\"indexing\": %s}]}".formatted(expected));
    }

    private DocumentContext project(String projectId) {
        return json(mvc.get().uri("/api/v1/projects/{id}", projectId).exchange());
    }

    private List<String> reasons(String projectId) {
        return project(projectId).read("$.outdatedReasons");
    }

    private void upload(UUID projectId, String name, String content) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry(name));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/projects/{id}/imports/zip", projectId)
                        .multipart()
                        .file(
                                new MockMultipartFile(
                                        "file", "a.zip", "application/zip", bytes.toByteArray()))
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String id = json(result).read("$.id");
        await().atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                json(mvc.get().uri("/api/v1/imports/{id}", id).exchange())
                                        .read("$.status"),
                        "COMPLETED"::equals);
    }
}
