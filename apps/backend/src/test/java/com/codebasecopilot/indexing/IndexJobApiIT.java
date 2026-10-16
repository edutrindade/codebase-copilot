package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertJob;
import static com.codebasecopilot.ImportJobRows.insertProjectJob;
import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.ImportJobRows.isJobActive;
import static com.codebasecopilot.ImportJobRows.setCurrentImport;
import static com.codebasecopilot.IndexRows.insertIndexJob;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.shared.datadir.DataDirectory;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Trees described in TestFileSystemInitializer, plus temporary trees in the work root.
@IntegrationTest
class IndexJobApiIT {

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private DataDirectory dataDirectory;

    private UUID workspaceId;
    private Path tree;

    @BeforeEach
    void setUp() throws IOException {
        DatabaseCleaner.truncateAll(jdbc);
        workspaceId = insertWorkspace(jdbc);
        tree =
                Files.createDirectories(
                        TestFileSystemInitializer.ROOTS_DIR.resolve(
                                "work/index-" + UUID.randomUUID()));
    }

    @AfterEach
    void removeTree() throws IOException {
        Directories.deleteRecursively(tree);
    }

    @Test
    void createsJobThatScansAndCompletes() {
        String projectId = localProject("work", "inventory-repo");

        MvcTestResult created = create(projectId);

        assertThat(created).hasStatus(HttpStatus.ACCEPTED);
        String id = read(created, "$.id");
        assertThat(created).hasHeader(HttpHeaders.LOCATION, "/api/v1/index-jobs/" + id);
        assertThat(created)
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"projectId": "%s", "status": "PENDING", "stage": null,
                         "progress": null, "attempts": 0, "error": null, "startedAt": null,
                         "completedAt": null}
                        """
                                .formatted(projectId));
        assertThat(awaitTerminal(id)).isEqualTo("COMPLETED");
        assertThat(job(id))
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"status": "COMPLETED", "stage": "FINALIZING", "attempts": 1,
                         "progress": {"total": 4}, "error": null}
                        """);
        assertThat(isJobActive(jdbc, UUID.fromString(id))).isFalse();
        assertThat(
                        jdbc.sql("SELECT inventory ->> 'truncated' FROM snapshots WHERE id = :id")
                                .param("id", UUID.fromString(read(created, "$.snapshotId")))
                                .query(String.class)
                                .single())
                .isEqualTo("false");
    }

    @Test
    void listsProjectJobsNewestFirst() {
        String projectId = localProject("work", "inventory-repo");
        String first = read(create(projectId), "$.id");
        awaitTerminal(first);
        String second = read(create(projectId), "$.id");
        awaitTerminal(second);

        assertThat(mvc.get().uri("/api/v1/projects/{id}/index-jobs?size=1", projectId))
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"items": [{"id": "%s"}], "page": 0, "size": 1, "totalItems": 2,
                         "totalPages": 2}
                        """
                                .formatted(second));
    }

    @Test
    void unknownJobAndProjectAreNotFound() {
        assertCode(job(UUID.randomUUID().toString()), HttpStatus.NOT_FOUND, "INDEX_JOB_NOT_FOUND");
        assertCode(
                mvc.get().uri("/api/v1/projects/{id}/index-jobs", UUID.randomUUID()).exchange(),
                HttpStatus.NOT_FOUND,
                "PROJECT_NOT_FOUND");
        assertCode(create(UUID.randomUUID().toString()), HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND");
    }

    @Test
    void sourceErrorsComeBeforeBusy() {
        String missing = localProject("work", "missing");
        insertProjectJob(jdbc, UUID.fromString(missing), "IMPORT", true);
        String zip = insertZipProject(jdbc, workspaceId).toString();
        insertProjectJob(jdbc, UUID.fromString(zip), "IMPORT", true);

        assertCode(create(missing), HttpStatus.CONFLICT, "SOURCE_DIRECTORY_NOT_FOUND");
        assertCode(create(zip), HttpStatus.CONFLICT, "SOURCE_NOT_IMPORTED");
        assertCode(
                create(localProject("default", "outside-link")),
                HttpStatus.CONFLICT,
                "SOURCE_IS_SYMLINK");
    }

    @Test
    void activeImportMakesProjectBusy() throws IOException {
        UUID projectId = insertZipProject(jdbc, workspaceId);
        UUID completed = insertJob(jdbc, projectId, "COMPLETED");
        setCurrentImport(jdbc, projectId, completed);
        Files.createDirectories(dataDirectory.importSourceDir(completed));
        insertJob(jdbc, projectId, "EXTRACTING");

        assertCode(create(projectId.toString()), HttpStatus.CONFLICT, "PROJECT_BUSY");
        assertThat(jdbc.sql("SELECT count(*) FROM index_jobs").query(Long.class).single()).isZero();
    }

    @Test
    void activeIndexJobBlocksUploadsIndexingAndRemoval() {
        String local = localProject("work", "inventory-repo");
        runningJob(UUID.fromString(local));
        UUID zip = insertZipProject(jdbc, workspaceId);
        runningJob(zip);

        assertCode(create(local), HttpStatus.CONFLICT, "PROJECT_BUSY");
        assertCode(
                mvc.delete().uri("/api/v1/projects/{id}", local).exchange(),
                HttpStatus.CONFLICT,
                "PROJECT_BUSY");
        assertCode(
                mvc.post()
                        .uri("/api/v1/projects/{id}/imports/zip", zip)
                        .multipart()
                        .file(new MockMultipartFile("file", "a.zip", "application/zip", zip()))
                        .exchange(),
                HttpStatus.CONFLICT,
                "PROJECT_BUSY");
    }

    @Test
    void folderWithoutIncludedFilesFails() {
        assertFailsWith(localProject("work", "only-excluded"), "NO_INCLUDED_FILES");
    }

    @Test
    void projectAboveSizeLimitFails() throws IOException {
        Files.writeString(tree.resolve("big.txt"), "x".repeat(2048));
        String projectId = localProject("work", tree.getFileName().toString());
        assertThat(
                        mvc.put()
                                .uri("/api/v1/projects/{id}/settings", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"maxProjectBytes\": 1024}"))
                .hasStatusOk();

        assertFailsWith(projectId, "PROJECT_SIZE_LIMIT_EXCEEDED");
    }

    @Test
    void folderRemovedBeforeTheJobRunsFails() throws IOException {
        Files.writeString(tree.resolve("a.txt"), "a");
        UUID projectId = UUID.fromString(localProject("work", tree.getFileName().toString()));
        Directories.deleteRecursively(tree);
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");

        UUID jobId = insertIndexJob(jdbc, projectId, snapshotId, "PENDING");

        assertThat(awaitTerminal(jobId.toString())).isEqualTo("FAILED");
        assertThat(read(job(jobId.toString()), "$.error.code"))
                .isEqualTo("SOURCE_DIRECTORY_NOT_FOUND");
        assertThat(isJobActive(jdbc, jobId)).isFalse();
    }

    @Test
    void jobsOfDifferentProjectsRunOnceEach() {
        String first = read(create(localProject("work", "inventory-repo")), "$.id");
        String second = read(create(localProject("default", "")), "$.id");

        awaitTerminal(first);
        awaitTerminal(second);
        assertThat(read(job(first), "$.attempts")).isEqualTo("1");
        assertThat(read(job(second), "$.attempts")).isEqualTo("1");
    }

    private void assertFailsWith(String projectId, String code) {
        MvcTestResult created = create(projectId);
        assertThat(created).hasStatus(HttpStatus.ACCEPTED);
        String id = read(created, "$.id");
        assertThat(awaitTerminal(id)).isEqualTo("FAILED");
        MvcTestResult result = job(id);
        assertThat(result)
                .bodyJson()
                .isLenientlyEqualTo("{\"error\": {\"code\": \"%s\"}}".formatted(code));
        assertThat(read(result, "$.error.message")).isNotBlank();
        assertThat(isJobActive(jdbc, UUID.fromString(id))).isFalse();
        assertThat(
                        jdbc.sql("SELECT status FROM snapshots WHERE id = :id")
                                .param("id", UUID.fromString(read(result, "$.snapshotId")))
                                .query(String.class)
                                .single())
                .isEqualTo("FAILED");
    }

    // RUNNING with a fresh heartbeat: the worker leaves it alone until the lease expires.
    private void runningJob(UUID projectId) {
        insertIndexJob(jdbc, projectId, insertSnapshot(jdbc, projectId, "BUILDING"), "RUNNING");
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

    private MvcTestResult create(String projectId) {
        return mvc.post().uri("/api/v1/projects/{id}/index-jobs", projectId).exchange();
    }

    private MvcTestResult job(String id) {
        return mvc.get().uri("/api/v1/index-jobs/{id}", id).exchange();
    }

    private String awaitTerminal(String id) {
        return await().atMost(Duration.ofSeconds(20))
                .until(() -> read(job(id), "$.status"), TERMINAL::contains);
    }

    private static byte[] zip() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry("a.txt"));
            out.write("a".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static String read(MvcTestResult result, String path) {
        return String.valueOf((Object) JsonPath.read(body(result), path));
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
