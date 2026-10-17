package com.codebasecopilot.retrieval;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.retrieval.IndexedProjects.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// The facade other modules use, over a snapshot built by the real pipeline.
@IntegrationTest
class RetrievalIT {

    private static final String SERVICE =
            "class RefundService {\n  long refund(Order order) {\n    return order.total();\n  }\n}\n";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private Retrieval retrieval;

    private IndexedProjects projects;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        projects = new IndexedProjects(mvc, insertWorkspace(jdbc));
    }

    @AfterEach
    void removeTrees() throws IOException {
        projects.removeTrees();
    }

    @Test
    void retrievesChunksWithWhatAnAnswerCopies() throws IOException {
        Path tree = projects.tree();
        write(tree, "src/RefundService.java", SERVICE);
        write(tree, "copy/RefundService.java", SERVICE);
        write(tree, "docs/notes.md", "Notes about the weather.\n");
        UUID projectId = projects.project(tree);
        UUID snapshotId = projects.index(projectId);

        Retrieval.Prepared prepared =
                retrieval.prepare(
                        new Retrieval.Request(
                                projectId,
                                "How is the refund computed?",
                                null,
                                SearchMode.HYBRID,
                                20));
        Retrieval.Result result = retrieval.retrieve(prepared);

        assertThat(prepared.snapshotId()).isEqualTo(snapshotId);
        assertThat(prepared.embeddingModel()).isNotNull();
        assertThat(result.snapshotId()).isEqualTo(snapshotId);
        assertThat(result.mode()).isEqualTo(SearchMode.HYBRID);
        assertThat(result.lexicalVersion()).isNotNull();
        assertThat(result.chunks()).isNotEmpty();
        RetrievedChunk first = result.chunks().getFirst();
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.paths())
                .containsExactly("copy/RefundService.java", "src/RefundService.java");
        assertThat(first.contentHash())
                .isEqualTo(
                        jdbc.sql("SELECT content_hash FROM chunks WHERE id = :id")
                                .param("id", first.chunkId())
                                .query(String.class)
                                .single());
        assertThat(first.tokenEstimate()).isEqualTo((first.content().length() + 3) / 4);
        assertThat(first.rankLexical()).isEqualTo(1);
        assertThat(result.chunks())
                .extracting(RetrievedChunk::rank)
                .containsExactlyElementsOf(
                        IntStream.rangeClosed(1, result.chunks().size()).boxed().toList());
    }

    @Test
    void lexicalModeHasNoEmbeddingModel() throws IOException {
        UUID snapshotId = projects.indexedSnapshot("src/RefundService.java", SERVICE);
        UUID projectId =
                jdbc.sql("SELECT project_id FROM snapshots WHERE id = :id")
                        .param("id", snapshotId)
                        .query(UUID.class)
                        .single();

        Retrieval.Prepared prepared =
                retrieval.prepare(
                        new Retrieval.Request(projectId, "refund", null, SearchMode.LEXICAL, 20));

        assertThat(prepared.embeddingModel()).isNull();
        assertThat(retrieval.retrieve(prepared).chunks())
                .extracting(chunk -> chunk.paths().getFirst())
                .containsExactly("src/RefundService.java");
    }

    @Test
    void prepareReportsTheSearchPreconditions() throws IOException {
        UUID projectId = projects.project(projects.tree());

        assertThatThrownBy(
                        () ->
                                retrieval.prepare(
                                        new Retrieval.Request(
                                                projectId, "refund", null, SearchMode.HYBRID, 20)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SNAPSHOT_NOT_SEARCHABLE));
    }
}
