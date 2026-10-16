package com.codebasecopilot.importjob;

import static com.codebasecopilot.ImportJobRows.insertJob;
import static com.codebasecopilot.ImportJobRows.insertProjectJob;
import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.ImportJobRows.isJobActive;
import static com.codebasecopilot.importjob.ZipFixtures.randomBytes;
import static com.codebasecopilot.importjob.ZipFixtures.raw;
import static com.codebasecopilot.importjob.ZipFixtures.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.shared.datadir.DataDirectory;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ImportApiIT {

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "REJECTED", "FAILED");
    private static final String SECRET_NAME = "SECRET-ENTRY-7f3a";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private DataDirectory dataDirectory;
    @Autowired private TransactionTemplate transactions;

    private UUID workspaceId;
    private UUID projectId;

    @BeforeEach
    void setUp() throws IOException {
        DatabaseCleaner.truncateAll(jdbc);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dataDirectory.importsDir())) {
            for (Path entry : entries) {
                Directories.deleteRecursively(entry);
            }
        }
        workspaceId = insertWorkspace(jdbc);
        projectId = insertZipProject(jdbc, workspaceId);
    }

    @Test
    void uploadIsAcceptedAndExtracted() {
        MvcTestResult result = upload(projectId, zip("src/Main.java", "class Main {}", "a", "b"));

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String id = read(result, "$.id");
        assertThat(result).hasHeader(HttpHeaders.LOCATION, "/api/v1/imports/" + id);
        assertThat(result)
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"projectId": "%s", "status": "PENDING", "uncompressedBytes": null,
                         "entryCount": null, "error": null, "completedAt": null}
                        """
                                .formatted(projectId));

        awaitTerminal(id);
        assertThat(mvc.get().uri("/api/v1/imports/" + id))
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"status": "COMPLETED", "entryCount": 2, "uncompressedBytes": 14,
                         "error": null}
                        """);
        assertThat(currentImport()).isEqualTo(id);
        Path dir = dataDirectory.importDir(UUID.fromString(id));
        assertThat(dir.resolve("source/src/Main.java")).hasContent("class Main {}");
        assertThat(dir.resolve("upload.zip")).doesNotExist();
        assertThat(isJobActive(jdbc, UUID.fromString(id))).isFalse();
    }

    @Test
    void newImportReplacesCurrentAndRemovesPreviousFiles() {
        String first = completedImport(zip("a.txt", "1"));
        String second = completedImport(zip("b.txt", "2"));

        assertThat(currentImport()).isEqualTo(second);
        assertThat(dataDirectory.importDir(UUID.fromString(first))).doesNotExist();
        assertThat(dataDirectory.importDir(UUID.fromString(second)).resolve("source/b.txt"))
                .exists();
    }

    @Test
    void rejectedImportKeepsCurrentAndHidesEntryNames(CapturedOutput output) {
        String current = completedImport(zip("a.txt", "1"));

        String rejected =
                read(upload(projectId, raw().file(SECRET_NAME + "/../../x", "x").build()), "$.id");
        awaitTerminal(rejected);

        MvcTestResult result = mvc.get().uri("/api/v1/imports/" + rejected).exchange();
        assertThat(result)
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"status": "REJECTED", "error": {"code": "ZIP_UNSAFE_PATH"}}
                        """);
        assertThat(read(result, "$.error.message")).startsWith("O ZIP contém caminhos inseguros");
        assertThat(body(result)).doesNotContain(SECRET_NAME);
        assertThat(output.getAll()).doesNotContain(SECRET_NAME);
        assertThat(currentImport()).isEqualTo(current);
        assertThat(dataDirectory.importDir(UUID.fromString(rejected))).doesNotExist();
        assertThat(isJobActive(jdbc, UUID.fromString(rejected))).isFalse();
        assertThat(upload(projectId, zip("b.txt", "2"))).hasStatus(HttpStatus.ACCEPTED);
    }

    @Test
    void uploadWithoutFileIsValidationFailure() {
        MvcTestResult missing =
                mvc.post()
                        .uri("/api/v1/projects/{id}/imports/zip", projectId)
                        .multipart()
                        .param("other", "x")
                        .exchange();
        MvcTestResult empty = upload(projectId, new byte[0]);

        for (MvcTestResult result : List.of(missing, empty)) {
            assertThat(result)
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .isLenientlyEqualTo(
                            """
                            {"code": "VALIDATION_FAILED", "errors": [{"field": "file"}]}
                            """);
        }
    }

    @Test
    void uploadAboveLimitIsTooLarge() {
        assertCode(
                upload(projectId, randomBytes(65 * 1024, 7)),
                HttpStatus.CONTENT_TOO_LARGE,
                "UPLOAD_TOO_LARGE");
    }

    @Test
    void unknownProjectIsNotFound() {
        assertCode(
                upload(UUID.randomUUID(), zip("a.txt", "a")),
                HttpStatus.NOT_FOUND,
                "PROJECT_NOT_FOUND");
    }

    @Test
    void localFolderProjectIsSourceMismatch() {
        UUID local = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO projects (id, workspace_id, name, source_type,
                                              source_root_key, source_relative_path,
                                              created_at, updated_at)
                        VALUES (:id, :workspaceId, 'L', 'LOCAL_FOLDER', 'default', 'a', now(),
                                now())
                        """)
                .param("id", local)
                .param("workspaceId", workspaceId)
                .update();

        assertCode(
                upload(local, zip("a.txt", "a")), HttpStatus.CONFLICT, "PROJECT_SOURCE_MISMATCH");
    }

    @Test
    void activeImportMakesProjectBusy() {
        insertJob(jdbc, projectId, "EXTRACTING");

        assertCode(upload(projectId, zip("a.txt", "a")), HttpStatus.CONFLICT, "PROJECT_BUSY");
    }

    @Test
    void activeIndexJobMakesProjectBusy() {
        insertProjectJob(jdbc, projectId, "INDEX", true);

        assertCode(upload(projectId, zip("a.txt", "a")), HttpStatus.CONFLICT, "PROJECT_BUSY");
        assertThat(jdbc.sql("SELECT count(*) FROM import_jobs").query(Long.class).single())
                .isZero();
    }

    @Test
    void concurrentUploadWaitsForTheFirstAndIsBusy() throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first =
                Thread.ofPlatform()
                        .start(
                                () ->
                                        transactions.executeWithoutResult(
                                                status -> {
                                                    insertJob(jdbc, projectId, "PENDING");
                                                    inserted.countDown();
                                                    awaitLatch(release);
                                                }));
        assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<MvcTestResult> second =
                CompletableFuture.supplyAsync(() -> upload(projectId, zip("a.txt", "a")));
        await().atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                jdbc.sql(
                                                        """
                                                SELECT count(*) FROM pg_stat_activity
                                                WHERE wait_event_type = 'Lock'
                                                """)
                                                .query(Long.class)
                                                .single()
                                        > 0);
        release.countDown();
        first.join();

        assertCode(second.get(10, TimeUnit.SECONDS), HttpStatus.CONFLICT, "PROJECT_BUSY");
    }

    @Test
    void nonMultipartRequestIsUnsupportedMediaType() {
        assertCode(
                mvc.post()
                        .uri("/api/v1/projects/{id}/imports/zip", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .exchange(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void listsProjectImportsNewestFirst() {
        String first = completedImport(zip("a.txt", "1"));
        String second = completedImport(zip("b.txt", "2"));

        assertThat(mvc.get().uri("/api/v1/projects/{id}/imports", projectId))
                .hasStatusOk()
                .bodyJson()
                .satisfies(
                        json -> {
                            assertThat(json)
                                    .extractingPath("$.items[*].id")
                                    .isEqualTo(List.of(second, first));
                            assertThat(json).extractingPath("$.totalItems").isEqualTo(2);
                        });
        assertCode(
                mvc.get().uri("/api/v1/projects/{id}/imports", UUID.randomUUID()).exchange(),
                HttpStatus.NOT_FOUND,
                "PROJECT_NOT_FOUND");
    }

    @Test
    void unknownImportIsNotFound() {
        assertCode(
                mvc.get().uri("/api/v1/imports/{id}", UUID.randomUUID()).exchange(),
                HttpStatus.NOT_FOUND,
                "IMPORT_NOT_FOUND");
    }

    private String completedImport(byte[] archive) {
        MvcTestResult result = upload(projectId, archive);
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String id = read(result, "$.id");
        assertThat(awaitTerminal(id)).isEqualTo("COMPLETED");
        return id;
    }

    private String awaitTerminal(String id) {
        return await().atMost(Duration.ofSeconds(10))
                .until(
                        () -> read(mvc.get().uri("/api/v1/imports/" + id).exchange(), "$.status"),
                        TERMINAL::contains);
    }

    private String currentImport() {
        return read(
                mvc.get().uri("/api/v1/projects/" + projectId).exchange(),
                "$.source.currentImport.id");
    }

    private MvcTestResult upload(UUID project, byte[] content) {
        return mvc.post()
                .uri("/api/v1/projects/{id}/imports/zip", project)
                .multipart()
                .file(new MockMultipartFile("file", "repo.zip", "application/zip", content))
                .exchange();
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }
}
