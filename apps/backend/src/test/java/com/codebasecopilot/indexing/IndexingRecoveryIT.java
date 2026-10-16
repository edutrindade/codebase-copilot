package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.isJobActive;
import static com.codebasecopilot.IndexRows.insertFile;
import static com.codebasecopilot.IndexRows.insertIndexJob;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static com.codebasecopilot.indexing.IndexingTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.DocumentContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class IndexingRecoveryIT extends SpiedReaderContext {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private IndexOutcomes outcomes;
    @Autowired private IndexJobRepository jobs;

    private IndexingTestClient client;
    private Path tree;

    @BeforeEach
    void setUp() throws IOException {
        DatabaseCleaner.truncateAll(jdbc);
        reset(reader);
        client = new IndexingTestClient(mvc);
        tree =
                Files.createDirectories(
                        TestFileSystemInitializer.ROOTS_DIR.resolve(
                                "work/index-" + UUID.randomUUID()));
        for (int i = 1; i <= 7; i++) {
            Files.writeString(tree.resolve("file" + i + ".txt"), "content " + (i % 3));
        }
        Files.writeString(tree.resolve("bad.txt"), "x".repeat(4001));
    }

    @AfterEach
    void removeTree() throws IOException {
        reset(reader);
        Directories.deleteRecursively(tree);
    }

    @Test
    void jobInterruptedInTheMiddleEndsLikeACleanRun() {
        String clean = project();
        String cleanSnapshot = client.index(clean);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            if (reads.incrementAndGet() == 5) {
                                throw new IllegalStateException("simulated crash");
                            }
                            return invocation.callRealMethod();
                        })
                .when(reader)
                .read(any(), anyLong());
        String interrupted = project();

        String snapshotId = client.index(interrupted);

        assertThat((Integer) jobOf(snapshotId).read("$.attempts")).isEqualTo(2);
        assertThat(files(snapshotId)).isEqualTo(files(cleanSnapshot));
        assertThat(contents(interrupted)).isEqualTo(contents(clean));
        assertThat(chunks(interrupted)).isEqualTo(chunks(clean));
        assertThat((Map<String, Object>) client.snapshot(snapshotId).read("$.counts"))
                .isEqualTo(client.snapshot(cleanSnapshot).read("$.counts"));
    }

    @Test
    void jobLeftRunningByADeadWorkerIsTakenOverOnceTheLeaseExpires() {
        UUID projectId = UUID.fromString(project());
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");
        UUID jobId = insertIndexJob(jdbc, projectId, snapshotId, "RUNNING");
        insertFile(jdbc, snapshotId, projectId, "stale.txt", "FAILED", "READ_ERROR", null);
        expire(jobId, 1);

        String status = awaitTerminal(jobId);

        assertThat(status).isEqualTo("COMPLETED");
        assertThat((Integer) client.job(jobId.toString()).read("$.attempts")).isEqualTo(2);
        assertThat(files(snapshotId.toString())).doesNotContainKey("stale.txt").hasSize(8);
        assertThat(isJobActive(jdbc, jobId)).isFalse();
    }

    @Test
    void jobWhoseWorkerDiedOnEveryAttemptFails() {
        UUID projectId = UUID.fromString(project());
        String active = client.index(projectId.toString());
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");
        UUID jobId = insertIndexJob(jdbc, projectId, snapshotId, "RUNNING");
        expire(jobId, 3);

        assertThat(awaitTerminal(jobId)).isEqualTo("FAILED");

        assertThat((String) client.job(jobId.toString()).read("$.error.code"))
                .isEqualTo("INDEX_ATTEMPTS_EXHAUSTED");
        assertThat((String) client.snapshot(snapshotId.toString()).read("$.status"))
                .isEqualTo("FAILED");
        assertThat((Boolean) client.snapshot(active).read("$.active")).isTrue();
        assertThat(isJobActive(jdbc, jobId)).isFalse();
    }

    @Test
    void errorOnEveryAttemptFailsWithoutChangingTheActiveSnapshot() {
        String projectId = project();
        String active = client.index(projectId);
        doThrow(new IllegalStateException("always")).when(reader).read(any(), anyLong());

        String id =
                json(mvc.post().uri("/api/v1/projects/{id}/index-jobs", projectId).exchange())
                        .read("$.id");

        assertThat(awaitTerminal(UUID.fromString(id))).isEqualTo("FAILED");
        DocumentContext job = client.job(id);
        assertThat((String) job.read("$.error.code")).isEqualTo("INDEX_IO_ERROR");
        assertThat((Integer) job.read("$.attempts")).isEqualTo(3);
        assertThat((Boolean) client.snapshot(active).read("$.active")).isTrue();
        assertThat(isJobActive(jdbc, UUID.fromString(id))).isFalse();
    }

    @Test
    void workerThatLostItsClaimCannotFinishTheJob() {
        UUID projectId = UUID.fromString(project());
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");
        UUID jobId = insertIndexJob(jdbc, projectId, snapshotId, "RUNNING");
        jdbc.sql("UPDATE index_jobs SET attempt_count = 2 WHERE id = :id")
                .param("id", jobId)
                .update();
        Claim late = new Claim(jobId, projectId, snapshotId, 1);

        assertThat(outcomes.complete(late, null)).isFalse();
        assertThat(outcomes.fail(late, IndexErrorCode.INDEX_IO_ERROR)).isFalse();
        assertThat(jobs.heartbeat(late, IndexStage.SCANNING, null, null, Instant.now())).isFalse();
        assertThat(jobs.requeue(late)).isFalse();

        assertThat((String) client.job(jobId.toString()).read("$.status")).isEqualTo("RUNNING");
        assertThat((String) client.snapshot(snapshotId.toString()).read("$.status"))
                .isEqualTo("BUILDING");
        assertThat(isJobActive(jdbc, jobId)).isTrue();
        expire(jobId, 2);
        awaitTerminal(jobId);
    }

    // A RUNNING job whose heartbeat is older than the lease, after the given attempts.
    private void expire(UUID jobId, int attempts) {
        jdbc.sql(
                        """
                        UPDATE index_jobs
                        SET attempt_count = :attempts,
                            heartbeat_at = now() - interval '1 hour'
                        WHERE id = :id
                        """)
                .param("id", jobId)
                .param("attempts", attempts)
                .update();
    }

    private String awaitTerminal(UUID jobId) {
        return await().atMost(Duration.ofSeconds(20))
                .until(
                        () -> client.job(jobId.toString()).read("$.status"),
                        s -> s.equals("COMPLETED") || s.equals("FAILED"));
    }

    private DocumentContext jobOf(String snapshotId) {
        String jobId =
                jdbc.sql("SELECT id::text FROM index_jobs WHERE snapshot_id = :id")
                        .param("id", UUID.fromString(snapshotId))
                        .query(String.class)
                        .single();
        return client.job(jobId);
    }

    private String project() {
        UUID workspaceId = insertWorkspace(jdbc);
        return client.localProject(workspaceId, "work", tree.getFileName().toString());
    }

    // relativePath -> status/reason/hash
    private Map<String, String> files(String snapshotId) {
        Map<String, String> files = new TreeMap<>();
        jdbc.sql(
                        """
                        SELECT relative_path, status || '/' || coalesce(reason, '') || '/'
                               || coalesce(content_hash, '') AS state
                        FROM snapshot_files WHERE snapshot_id = :id
                        """)
                .param("id", UUID.fromString(snapshotId))
                .query(
                        rs -> {
                            files.put(rs.getString("relative_path"), rs.getString("state"));
                        });
        return files;
    }

    private List<String> contents(String projectId) {
        return jdbc.sql("SELECT content_hash FROM file_contents WHERE project_id = :id ORDER BY 1")
                .param("id", UUID.fromString(projectId))
                .query(String.class)
                .list();
    }

    private List<String> chunks(String projectId) {
        return jdbc.sql(
                        """
                        SELECT content_hash || '/' || ordinal || '/' || chunk_hash
                        FROM chunks WHERE project_id = :id ORDER BY 1
                        """)
                .param("id", UUID.fromString(projectId))
                .query(String.class)
                .list();
    }
}
