package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.IndexRows.count;
import static com.codebasecopilot.indexing.IndexingTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.DocumentContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// The LEXICAL stage of the index job (D-056), without an embeddings provider and with small
// batches so a crash can happen between two of them.
@IntegrationTest
@TestPropertySource(
        properties = {"app.embeddings.provider=none", "app.indexing.lexical-batch-chunks=2"})
class LexicalIndexingIT {

    private static final String CODE = "UserRepository repo;\nrepo.getUserById(id);";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @MockitoSpyBean private ChunkLexicalRepository lexical;

    private IndexingTestClient client;
    private UUID workspaceId;
    private final List<Path> trees = new ArrayList<>();

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        reset(lexical);
        client = new IndexingTestClient(mvc);
        workspaceId = insertWorkspace(jdbc);
    }

    @AfterEach
    void removeTrees() throws IOException {
        reset(lexical);
        for (Path tree : trees) {
            Directories.deleteRecursively(tree);
        }
    }

    @Test
    void newJobFillsEveryChunkOfTheSnapshotWithoutAProvider() throws IOException {
        Path tree = threeContents();
        Files.writeString(tree.resolve("copy.java"), CODE);
        Files.write(tree.resolve("blob.bin"), new byte[] {0, 1, 2, 0, 3});
        String projectId = project(tree);

        String snapshotId = client.index(projectId);

        assertThat(
                        lexical.missingChunkIds(
                                UUID.fromString(snapshotId), IdentifierTokenizer.VERSION))
                .isEmpty();
        assertThat(count(jdbc, "chunk_lexical")).isEqualTo(count(jdbc, "chunks")).isEqualTo(3);
        assertThat(termsByContent(projectId))
                .containsEntry(CODE, String.join(" ", IdentifierTokenizer.identifierTerms(CODE)));
        assertThat(
                        jdbc.sql(
                                        """
                                        SELECT bool_and(l.tsv @@ to_tsquery('simple', 'user & id'))
                                        FROM chunk_lexical l JOIN chunks c ON c.id = l.chunk_id
                                        WHERE c.content = :content
                                        """)
                                .param("content", CODE)
                                .query(Boolean.class)
                                .single())
                .isTrue();
        DocumentContext snapshot = client.snapshot(snapshotId);
        assertThat((String) snapshot.read("$.lexical.version")).isEqualTo("ident-v1");
        assertThat((Object) snapshot.read("$.embedding")).isNull();
        assertThat(count(jdbc, "chunk_embeddings")).isZero();
        assertThat(outdatedReasons(projectId)).isEmpty();
    }

    @Test
    void reindexingWithoutChangesWritesNothing() throws IOException {
        String projectId = project(threeContents());
        client.index(projectId);
        List<String> before = rowVersions();
        reset(lexical);

        client.index(projectId);

        verify(lexical, never()).insert(anyString(), any());
        assertThat(rowVersions()).isEqualTo(before);
    }

    @Test
    void snapshotWithoutLexicalIndexIsFilledByReindexingWithoutChangedFiles() throws IOException {
        String projectId = project(threeContents());
        String first = client.index(projectId);
        indexedBeforeV9(first);

        assertThat(outdatedReasons(projectId)).containsExactly("LEXICAL_INDEX_MISSING");
        assertThat((Object) client.snapshot(first).read("$.lexical")).isNull();

        String second = client.index(projectId);

        DocumentContext snapshot = client.snapshot(second);
        assertThat((Integer) snapshot.read("$.changes.unchanged")).isEqualTo(3);
        assertThat((String) snapshot.read("$.lexical.version")).isEqualTo("ident-v1");
        assertThat(count(jdbc, "chunk_lexical")).isEqualTo(3);
        assertThat(outdatedReasons(projectId)).isEmpty();
    }

    @Test
    void anotherTokenizerVersionIsReported() throws IOException {
        String projectId = project(threeContents());
        String snapshotId = client.index(projectId);
        jdbc.sql("UPDATE snapshots SET lexical_version = 'ident-v0' WHERE id = :id")
                .param("id", UUID.fromString(snapshotId))
                .update();

        assertThat(outdatedReasons(projectId)).containsExactly("LEXICAL_INDEX_CHANGED");
    }

    @Test
    void jobInterruptedInTheMiddleOfTheStageEndsLikeACleanRun() throws IOException {
        String clean = project(threeContents());
        client.index(clean);
        reset(lexical);
        AtomicInteger inserts = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            if (inserts.incrementAndGet() == 2) {
                                throw new IllegalStateException("simulated crash");
                            }
                            return invocation.callRealMethod();
                        })
                .when(lexical)
                .insert(anyString(), any());
        String interrupted = project(threeContents());

        String snapshotId = client.index(interrupted);

        assertThat((Integer) jobOf(snapshotId).read("$.attempts")).isEqualTo(2);
        // First attempt: one batch stored, one crashed; the second sends only what is missing.
        verify(lexical, times(3)).insert(anyString(), any());
        assertThat(termsByContent(interrupted)).isEqualTo(termsByContent(clean));
        assertThat(tsvByContent(interrupted)).isEqualTo(tsvByContent(clean));
    }

    @Test
    void removingTheProjectRemovesItsLexicalRows() throws IOException {
        String projectId = project(threeContents());
        client.index(projectId);

        assertThat(mvc.delete().uri("/api/v1/projects/{id}", projectId).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(count(jdbc, "chunk_lexical")).isZero();
    }

    // The state of a snapshot indexed before V9: no lexical rows and no lexical version.
    private void indexedBeforeV9(String snapshotId) {
        jdbc.sql("DELETE FROM chunk_lexical").update();
        jdbc.sql("UPDATE snapshots SET lexical_version = NULL WHERE id = :id")
                .param("id", UUID.fromString(snapshotId))
                .update();
    }

    private Map<String, String> termsByContent(String projectId) {
        return byContent(projectId, "l.identifier_terms");
    }

    private Map<String, String> tsvByContent(String projectId) {
        return byContent(projectId, "l.tsv::text");
    }

    private Map<String, String> byContent(String projectId, String column) {
        return jdbc
                .sql(
                        "SELECT c.content, "
                                + column
                                + """
                                 AS value
                                FROM chunk_lexical l JOIN chunks c ON c.id = l.chunk_id
                                WHERE c.project_id = :projectId
                                """)
                .param("projectId", UUID.fromString(projectId))
                .query((rs, rowNum) -> Map.entry(rs.getString("content"), rs.getString("value")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // xmin changes whenever a row is written again.
    private List<String> rowVersions() {
        return jdbc.sql(
                        """
                        SELECT chunk_id::text || ':' || xmin::text
                        FROM chunk_lexical ORDER BY chunk_id
                        """)
                .query(String.class)
                .list();
    }

    private DocumentContext jobOf(String snapshotId) {
        String jobId =
                jdbc.sql("SELECT id::text FROM index_jobs WHERE snapshot_id = :id")
                        .param("id", UUID.fromString(snapshotId))
                        .query(String.class)
                        .single();
        return client.job(jobId);
    }

    private List<String> outdatedReasons(String projectId) {
        return json(mvc.get().uri("/api/v1/projects/{id}", projectId).exchange())
                .read("$.outdatedReasons");
    }

    // Three files with distinct contents, one chunk each: two lexical batches.
    private Path threeContents() throws IOException {
        Path tree =
                Files.createDirectories(
                        TestFileSystemInitializer.ROOTS_DIR.resolve(
                                "work/lexical-" + UUID.randomUUID()));
        trees.add(tree);
        Files.writeString(tree.resolve("UserRepository.java"), CODE);
        Files.writeString(tree.resolve("notes.md"), "Plain prose about users.");
        Files.writeString(tree.resolve("config.yml"), "base_url: https://example.com/api/v1");
        return tree;
    }

    private String project(Path tree) {
        return client.localProject(workspaceId, "work", tree.getFileName().toString());
    }
}
