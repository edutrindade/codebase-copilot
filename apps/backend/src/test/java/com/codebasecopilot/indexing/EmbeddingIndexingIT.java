package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.IndexRows.count;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.providers.EmbeddingFailure;
import com.codebasecopilot.providers.EmbeddingFailure.Type;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.DocumentContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// The EMBEDDING stage of the index job (D-050, D-051, D-052, D-054).
@ExtendWith(OutputCaptureExtension.class)
class EmbeddingIndexingIT extends EmbeddingContext {

    private static final String SECRET_TERM = "kept-out-of-logs-91c2";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private ChunkEmbeddingRepository embeddings;
    @Autowired private EmbeddingOrphanSweep sweep;

    private IndexingTestClient client;
    private UUID workspaceId;
    private final List<Path> trees = new ArrayList<>();

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        reset(provider);
        client = new IndexingTestClient(mvc);
        workspaceId = insertWorkspace(jdbc);
    }

    @AfterEach
    void removeTrees() throws IOException {
        reset(provider);
        for (Path tree : trees) {
            Directories.deleteRecursively(tree);
        }
    }

    @Test
    void everyChunkOfTheSnapshotGetsAVectorOfTheModel(CapturedOutput output) throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha beta " + SECRET_TERM);
        write(tree, "b.txt", "gamma delta");
        write(tree, "same-as-b.txt", "gamma delta");
        Files.write(tree.resolve("blob.bin"), new byte[] {0, 1, 2, 0, 3});

        String snapshotId = client.index(project(tree));

        ModelInfo model = provider.model();
        assertThat(embeddings.missingChunkHashes(UUID.fromString(snapshotId), model)).isEmpty();
        assertThat(count(jdbc, "chunk_embeddings")).isEqualTo(2);
        assertThat(sentTexts())
                .containsExactlyInAnyOrder("alpha beta " + SECRET_TERM, "gamma delta");
        // The fake reports one token per term.
        assertThat(embeddingColumns(snapshotId))
                .containsEntry("embedding_provider", "fake")
                .containsEntry("embedding_model", model.model())
                .containsEntry("embedding_dimensions", model.dimensions())
                .containsEntry("embedding_tokens", 9L);
        assertThat(output).doesNotContain(SECRET_TERM).contains("embedded 2 chunks in 1 batches");
    }

    @Test
    void reindexingWithoutChangesDoesNotCallTheProvider() throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha");
        String projectId = project(tree);
        client.index(projectId);
        reset(provider);

        String snapshotId = client.index(projectId);

        verify(provider, never()).embed(any());
        assertThat(embeddingColumns(snapshotId))
                .containsEntry("embedding_provider", "fake")
                .containsEntry("embedding_tokens", 0L);
    }

    @Test
    void changedFileSendsOnlyItsNewChunks() throws IOException {
        Path tree = tree();
        write(tree, "keep.txt", "keep");
        write(tree, "change.txt", "before");
        String projectId = project(tree);
        client.index(projectId);
        reset(provider);
        write(tree, "change.txt", "after");

        client.index(projectId);

        assertThat(sentTexts()).containsExactly("after");
    }

    @ParameterizedTest
    @CsvSource({
        "UNAUTHORIZED, EMBEDDING_PROVIDER_UNAUTHORIZED",
        "QUOTA_EXCEEDED, EMBEDDING_QUOTA_EXCEEDED",
        "INVALID_REQUEST, EMBEDDING_REQUEST_REJECTED"
    })
    void permanentFailureEndsTheJobAtOnce(Type type, String code) throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha");
        String projectId = project(tree);
        String active = client.index(projectId);
        write(tree, "b.txt", "beta");
        reset(provider);
        doThrow(new EmbeddingFailure(type, "simulated")).when(provider).embed(any());

        DocumentContext job = client.runJob(projectId);

        assertFailed(job, code);
        verify(provider, times(1)).embed(any());
        assertThat((Boolean) client.snapshot(active).read("$.active")).isTrue();
        assertThat((String) client.snapshot(job.read("$.snapshotId")).read("$.status"))
                .isEqualTo("FAILED");
    }

    @Test
    void quotaFailureShowsThePortugueseMessage() throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha");
        doThrow(new EmbeddingFailure(Type.QUOTA_EXCEEDED, "simulated")).when(provider).embed(any());

        DocumentContext job = client.runJob(project(tree));

        assertThat((String) job.read("$.error.message"))
                .isEqualTo(
                        "A cota ou o limite de gastos da conta no provedor de embeddings foi"
                                + " atingido. Verifique os créditos e os limites da conta e tente"
                                + " novamente.");
    }

    @Test
    void unavailableProviderIsRetriedAndThenFailsTheJob() throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha");
        doThrow(new EmbeddingFailure(Type.UNAVAILABLE, 503, null, "simulated"))
                .when(provider)
                .embed(any());

        DocumentContext job = client.runJob(project(tree));

        assertFailed(job, "EMBEDDING_PROVIDER_UNAVAILABLE");
        // One call and app.embeddings.max-retries (4) retries.
        verify(provider, times(5)).embed(any());
    }

    @Test
    void nextJobReusesTheEmbeddingsOfAFailedJob() throws IOException {
        Path tree = tree();
        for (int i = 1; i <= 6; i++) {
            write(tree, "file" + i + ".txt", "term" + i);
        }
        String projectId = project(tree);
        doCallRealMethod()
                .doThrow(new EmbeddingFailure(Type.UNAUTHORIZED, "simulated"))
                .when(provider)
                .embed(any());

        assertFailed(client.runJob(projectId), "EMBEDDING_PROVIDER_UNAUTHORIZED");
        assertThat(count(jdbc, "chunk_embeddings")).isEqualTo(4);
        reset(provider);

        String snapshotId = client.index(projectId);

        assertThat(sentTexts()).hasSize(2);
        assertThat(count(jdbc, "chunk_embeddings")).isEqualTo(6);
        assertThat(embeddings.missingChunkHashes(UUID.fromString(snapshotId), provider.model()))
                .isEmpty();
    }

    @Test
    void orphansOfARemovedProjectDisappearAfterAnotherJob() throws IOException {
        Path removedTree = tree();
        write(removedTree, "only-here.txt", "only here");
        write(removedTree, "shared.txt", "shared text");
        String removed = project(removedTree);
        client.index(removed);
        Path keptTree = tree();
        write(keptTree, "kept.txt", "shared text");
        String kept = project(keptTree);
        client.index(kept);
        assertThat(mvc.delete().uri("/api/v1/projects/{id}", removed).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(count(jdbc, "chunk_embeddings")).isEqualTo(2);
        write(keptTree, "new.txt", "new text");

        client.index(kept);

        assertThat(storedTexts()).containsExactlyInAnyOrder("shared text", "new text");
    }

    @Test
    void startupSweepRemovesOrphans() throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha");
        String projectId = project(tree);
        client.index(projectId);
        assertThat(mvc.delete().uri("/api/v1/projects/{id}", projectId).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);

        sweep.sweep();

        assertThat(count(jdbc, "chunk_embeddings")).isZero();
    }

    @Test
    void jobAboveTheChunkLimitFailsWithoutCallingTheProvider() throws IOException {
        Path tree = tree();
        for (int i = 1; i <= 21; i++) {
            write(tree, "file" + i + ".txt", "term" + i);
        }

        DocumentContext job = client.runJob(project(tree));

        assertFailed(job, "EMBEDDING_CHUNK_LIMIT_EXCEEDED");
        verify(provider, never()).embed(any());
        assertThat(count(jdbc, "chunk_embeddings")).isZero();
    }

    @Test
    void chunkLimitCountsOnlyTheChunksToSend() throws IOException {
        Path tree = tree();
        for (int i = 1; i <= 15; i++) {
            write(tree, "file" + i + ".txt", "term" + i);
        }
        String projectId = project(tree);
        client.index(projectId);
        for (int i = 16; i <= 30; i++) {
            write(tree, "file" + i + ".txt", "term" + i);
        }
        client.index(projectId);
        reset(provider);

        String snapshotId = client.index(projectId);

        verify(provider, never()).embed(any());
        assertThat((Integer) client.snapshot(snapshotId).read("$.counts.chunks")).isEqualTo(30);
    }

    // Retry-After of 300 s is capped at max-retry-wait (60 s); four waits add up to four times
    // the lease, so only the renewals keep another worker from taking the job over.
    @Test
    void waitsBetweenRetriesRenewTheLease() throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha");
        doThrow(new EmbeddingFailure(Type.RATE_LIMITED, 429, Duration.ofSeconds(300), "simulated"))
                .when(provider)
                .embed(any());

        DocumentContext job = client.runJob(project(tree));

        assertFailed(job, "EMBEDDING_RATE_LIMITED");
        verify(provider, times(5)).embed(any());
        double renewedForSeconds =
                jdbc.sql(
                                """
                                SELECT extract(epoch FROM heartbeat_at - started_at)
                                FROM index_jobs WHERE id = :id
                                """)
                        .param("id", UUID.fromString(job.read("$.id")))
                        .query(Double.class)
                        .single();
        assertThat(renewedForSeconds).isGreaterThanOrEqualTo(240);
    }

    private void assertFailed(DocumentContext job, String code) {
        assertThat((String) job.read("$.status"))
                .as("job %s", job.jsonString())
                .isEqualTo("FAILED");
        assertThat((String) job.read("$.error.code")).isEqualTo(code);
        assertThat((Integer) job.read("$.attempts")).isEqualTo(1);
    }

    private List<String> sentTexts() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
        verify(provider, atLeast(0)).embed(texts.capture());
        return texts.getAllValues().stream().flatMap(List::stream).toList();
    }

    private List<String> storedTexts() {
        return jdbc.sql(
                        """
                        SELECT DISTINCT c.content
                        FROM chunk_embeddings e JOIN chunks c ON c.chunk_hash = e.chunk_hash
                        """)
                .query(String.class)
                .list();
    }

    private Map<String, Object> embeddingColumns(String snapshotId) {
        return jdbc.sql(
                        """
                        SELECT embedding_provider, embedding_model, embedding_dimensions,
                               embedding_tokens
                        FROM snapshots WHERE id = :id
                        """)
                .param("id", UUID.fromString(snapshotId))
                .query()
                .singleRow();
    }

    private Path tree() throws IOException {
        Path tree =
                Files.createDirectories(
                        TestFileSystemInitializer.ROOTS_DIR.resolve(
                                "work/embed-" + UUID.randomUUID()));
        trees.add(tree);
        return tree;
    }

    private String project(Path tree) {
        return client.localProject(workspaceId, "work", tree.getFileName().toString());
    }

    private static void write(Path tree, String name, String content) throws IOException {
        Files.writeString(tree.resolve(name), content);
    }
}
