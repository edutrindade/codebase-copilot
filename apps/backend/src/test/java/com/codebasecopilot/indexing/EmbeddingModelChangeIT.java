package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.indexing.IndexingTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.DocumentContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Snapshot embedding state, model change and snapshots indexed without a provider (D-050,
// D-052, D-053).
class EmbeddingModelChangeIT extends EmbeddingContext {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

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
    void snapshotDetailAndListShowTheEmbeddingModel() throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha beta");
        String projectId = project(tree);
        String snapshotId = client.index(projectId);
        ModelInfo model = provider.model();

        DocumentContext detail = client.snapshot(snapshotId);
        DocumentContext list =
                json(mvc.get().uri("/api/v1/projects/{id}/snapshots", projectId).exchange());

        // The fake reports one token per term.
        Map<String, Object> expected =
                Map.of(
                        "provider",
                        "fake",
                        "model",
                        model.model(),
                        "dimensions",
                        model.dimensions(),
                        "tokens",
                        2);
        assertThat((Map<String, Object>) detail.read("$.embedding")).isEqualTo(expected);
        assertThat((Map<String, Object>) list.read("$.items[0].embedding")).isEqualTo(expected);
        assertThat(outdatedReasons(projectId)).isEmpty();
    }

    @Test
    void modelChangeIsReportedAndReindexingEmbedsForTheNewModelKeepingTheOldCache()
            throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha");
        write(tree, "b.txt", "beta");
        String projectId = project(tree);
        client.index(projectId);
        ModelInfo old = provider.model();
        ModelInfo changed = new ModelInfo(old.provider(), old.model() + "-v2", old.dimensions());
        reset(provider);
        doReturn(changed).when(provider).model();

        assertThat(outdatedReasons(projectId)).containsExactly("EMBEDDING_MODEL_CHANGED");
        assertMismatch(search(projectId));
        verify(provider, never()).embed(any());

        String snapshotId = client.index(projectId);

        assertThat(sentTexts()).containsExactlyInAnyOrder("alpha", "beta");
        assertThat((String) client.snapshot(snapshotId).read("$.embedding.model"))
                .isEqualTo(changed.model());
        assertThat(vectorsByModel())
                .containsExactlyInAnyOrderEntriesOf(Map.of(old.model(), 2L, changed.model(), 2L));
        assertThat(outdatedReasons(projectId)).isEmpty();
        MvcTestResult result = search(projectId);
        assertThat(result).hasStatusOk();
        assertThat((String) json(result).read("$.model.name")).isEqualTo(changed.model());
    }

    @Test
    void snapshotWithoutEmbeddingsIsCompletedByReindexingWithoutChangedFiles() throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha");
        write(tree, "b.txt", "beta");
        String projectId = project(tree);
        String first = client.index(projectId);
        indexedWithoutProvider(first);
        reset(provider);

        assertThat(outdatedReasons(projectId)).containsExactly("EMBEDDINGS_MISSING");
        assertThat((Object) client.snapshot(first).read("$.embedding")).isNull();
        assertMismatch(search(projectId));

        String second = client.index(projectId);

        assertThat(second).isNotEqualTo(first);
        DocumentContext snapshot = client.snapshot(second);
        assertThat((Integer) snapshot.read("$.changes.unchanged")).isEqualTo(2);
        assertThat((Boolean) snapshot.read("$.active")).isTrue();
        assertThat((String) snapshot.read("$.embedding.provider")).isEqualTo("fake");
        assertThat(sentTexts()).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(outdatedReasons(projectId)).isEmpty();
        MvcTestResult result = search(projectId);
        assertThat(result).hasStatusOk();
        assertThat((String) json(result).read("$.snapshotId")).isEqualTo(second);
        assertThat((List<String>) json(result).read("$.results[*].content"))
                .containsExactlyInAnyOrder("alpha", "beta");
    }

    // The state a job with provider=none leaves: a snapshot without model metadata and no
    // vectors for its chunks.
    private void indexedWithoutProvider(String snapshotId) {
        jdbc.sql(
                        """
                        UPDATE snapshots SET embedding_provider = NULL, embedding_model = NULL,
                                             embedding_dimensions = NULL, embedding_tokens = NULL
                        WHERE id = :id
                        """)
                .param("id", UUID.fromString(snapshotId))
                .update();
        jdbc.sql("DELETE FROM chunk_embeddings").update();
    }

    private void assertMismatch(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat((String) json(result).read("$.code")).isEqualTo("SNAPSHOT_EMBEDDING_MISMATCH");
    }

    private MvcTestResult search(String projectId) {
        return mvc.post()
                .uri("/api/v1/projects/{id}/search", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\": \"alpha beta\"}")
                .exchange();
    }

    private List<String> outdatedReasons(String projectId) {
        return json(mvc.get().uri("/api/v1/projects/{id}", projectId).exchange())
                .read("$.outdatedReasons");
    }

    private List<String> sentTexts() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
        verify(provider, atLeast(0)).embed(texts.capture());
        return texts.getAllValues().stream().flatMap(List::stream).toList();
    }

    private Map<String, Long> vectorsByModel() {
        return jdbc
                .sql("SELECT model, count(*) AS n FROM chunk_embeddings GROUP BY model")
                .query((rs, rowNum) -> Map.entry(rs.getString("model"), rs.getLong("n")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Path tree() throws IOException {
        Path tree =
                Files.createDirectories(
                        TestFileSystemInitializer.ROOTS_DIR.resolve(
                                "work/model-" + UUID.randomUUID()));
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
