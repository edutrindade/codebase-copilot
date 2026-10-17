package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.isJobActive;
import static com.codebasecopilot.IndexRows.insertIndexJob;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static com.codebasecopilot.indexing.IndexingTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.DocumentContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// POST /index-jobs/{id}/cancel (D-084). The spied reader holds jobs in PROCESSING_FILES.
class IndexCancelIT extends SpiedReaderContext {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

    private IndexingTestClient client;
    private Path tree;
    private CountDownLatch release = new CountDownLatch(0);

    @BeforeEach
    void setUp() throws IOException {
        DatabaseCleaner.truncateAll(jdbc);
        reset(reader);
        client = new IndexingTestClient(mvc);
        tree =
                Files.createDirectories(
                        TestFileSystemInitializer.ROOTS_DIR.resolve(
                                "work/index-" + UUID.randomUUID()));
        for (int i = 1; i <= 6; i++) {
            Files.writeString(tree.resolve("file" + i + ".txt"), "content " + i);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        release.countDown();
        reset(reader);
        Directories.deleteRecursively(tree);
    }

    @Test
    void runningJobStopsAtTheNextBatchAndKeepsTheActiveSnapshot() throws InterruptedException {
        String projectId = project();
        String active = client.index(projectId);
        CountDownLatch entered = holdReads(1);
        String jobId = start(projectId);
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

        MvcTestResult result = cancel(jobId);

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result)
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"status": "RUNNING", "cancelRequested": true}
                        """);
        assertThat(mvc.get().uri("/api/v1/projects/{id}", projectId))
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"indexing": {"id": "%s", "cancelRequested": true}}
                        """
                                .formatted(jobId));
        release.countDown();
        assertThat(awaitEnd(jobId)).isEqualTo("CANCELLED");
        DocumentContext job = client.job(jobId);
        assertThat((Object) job.read("$.error")).isNull();
        String snapshotId = job.read("$.snapshotId");
        assertThat((String) client.snapshot(snapshotId).read("$.status")).isEqualTo("CANCELLED");
        assertThat(filesOf(snapshotId)).isZero();
        assertThat((Boolean) client.snapshot(active).read("$.active")).isTrue();
        assertThat(isJobActive(jdbc, UUID.fromString(jobId))).isFalse();
        assertThat(mvc.get().uri("/api/v1/projects/{id}", projectId))
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"activeSnapshot": {"id": "%s"}, "indexing": null,
                         "lastJob": {"id": "%s", "status": "CANCELLED", "errorCode": null}}
                        """
                                .formatted(active, jobId));
        assertCode(cancel(jobId), HttpStatus.CONFLICT, "JOB_NOT_ACTIVE");
        assertThat(mvc.post().uri("/api/v1/projects/{id}/index-jobs", projectId))
                .hasStatus(HttpStatus.ACCEPTED);
    }

    @Test
    void pendingJobEndsRightAway() throws InterruptedException {
        CountDownLatch entered = holdReads(2);
        start(project());
        start(project());
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        String projectId = project();
        String jobId = start(projectId);

        MvcTestResult result = cancel(jobId);

        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result)
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"status": "CANCELLED", "cancelRequested": true, "attempts": 0}
                        """);
        assertThat((Object) json(result).read("$.completedAt")).isNotNull();
        String snapshotId = json(result).read("$.snapshotId");
        assertThat((String) client.snapshot(snapshotId).read("$.status")).isEqualTo("CANCELLED");
        assertThat(isJobActive(jdbc, UUID.fromString(jobId))).isFalse();
        release.countDown();
        assertThat(client.runJob(projectId).read("$.status").toString()).isEqualTo("COMPLETED");
    }

    @Test
    void requestBeforeACrashIsHonouredByTheNextClaim() {
        UUID projectId = UUID.fromString(project());
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");
        UUID jobId = insertIndexJob(jdbc, projectId, snapshotId, "RUNNING");

        assertThat(cancel(jobId.toString())).hasStatus(HttpStatus.ACCEPTED);
        jdbc.sql(
                        """
                        UPDATE index_jobs
                        SET attempt_count = 1, heartbeat_at = now() - interval '1 hour'
                        WHERE id = :id
                        """)
                .param("id", jobId)
                .update();

        assertThat(awaitEnd(jobId.toString())).isEqualTo("CANCELLED");
        assertThat((Integer) client.job(jobId.toString()).read("$.attempts")).isEqualTo(1);
        assertThat((String) client.snapshot(snapshotId.toString()).read("$.status"))
                .isEqualTo("CANCELLED");
        assertThat(isJobActive(jdbc, jobId)).isFalse();
    }

    @Test
    void endedAndUnknownJobsCannotBeCancelled() {
        UUID projectId = UUID.fromString(project());
        UUID failed =
                insertIndexJob(
                        jdbc, projectId, insertSnapshot(jdbc, projectId, "FAILED"), "FAILED");

        assertCode(cancel(failed.toString()), HttpStatus.CONFLICT, "JOB_NOT_ACTIVE");
        assertCode(
                cancel(UUID.randomUUID().toString()), HttpStatus.NOT_FOUND, "INDEX_JOB_NOT_FOUND");
    }

    // Every read waits for release; the returned latch opens after the given number of reads.
    private CountDownLatch holdReads(int reads) {
        CountDownLatch entered = new CountDownLatch(reads);
        CountDownLatch gate = new CountDownLatch(1);
        release = gate;
        doAnswer(
                        invocation -> {
                            entered.countDown();
                            gate.await(20, TimeUnit.SECONDS);
                            return invocation.callRealMethod();
                        })
                .when(reader)
                .read(any(), anyLong());
        return entered;
    }

    private String project() {
        return client.localProject(insertWorkspace(jdbc), "work", tree.getFileName().toString());
    }

    private String start(String projectId) {
        MvcTestResult created =
                mvc.post().uri("/api/v1/projects/{id}/index-jobs", projectId).exchange();
        assertThat(created).hasStatus(HttpStatus.ACCEPTED);
        return json(created).read("$.id");
    }

    private MvcTestResult cancel(String jobId) {
        return mvc.post().uri("/api/v1/index-jobs/{id}/cancel", jobId).exchange();
    }

    private String awaitEnd(String jobId) {
        return await().atMost(Duration.ofSeconds(20))
                .until(
                        () -> client.job(jobId).read("$.status"),
                        s -> !s.equals("PENDING") && !s.equals("RUNNING"));
    }

    private long filesOf(String snapshotId) {
        return jdbc.sql("SELECT count(*) FROM snapshot_files WHERE snapshot_id = :id")
                .param("id", UUID.fromString(snapshotId))
                .query(Long.class)
                .single();
    }

    private static void assertCode(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat((String) json(result).read("$.code")).isEqualTo(code);
    }
}
