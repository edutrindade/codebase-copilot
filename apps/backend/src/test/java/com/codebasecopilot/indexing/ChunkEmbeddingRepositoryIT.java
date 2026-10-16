package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.IndexRows.count;
import static com.codebasecopilot.IndexRows.insertContent;
import static com.codebasecopilot.IndexRows.insertFile;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.indexing.ChunkEmbeddingRepository.NewEmbedding;
import com.codebasecopilot.providers.ModelInfo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class ChunkEmbeddingRepositoryIT {

    private static final ModelInfo SMALL = new ModelInfo("fake", "small", 2);
    private static final ModelInfo OTHER = new ModelInfo("fake", "other", 2);

    @Autowired private ChunkEmbeddingRepository repository;
    @Autowired private JdbcClient jdbc;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        projectId = insertZipProject(jdbc, insertWorkspace(jdbc));
    }

    @Test
    void insertStoresTheVectorAndSkipsRowsAlreadyCached() {
        assertThat(repository.insert(SMALL, List.of(embedding("a"), embedding("b")), now()))
                .isEqualTo(2);
        assertThat(repository.insert(SMALL, List.of(embedding("a"), embedding("c")), now()))
                .isEqualTo(1);
        assertThat(repository.insert(SMALL, List.of(embedding("a")), now())).isZero();

        assertThat(count(jdbc, "chunk_embeddings")).isEqualTo(3);
        assertThat(
                        jdbc.sql(
                                        "SELECT embedding::text FROM chunk_embeddings"
                                                + " WHERE chunk_hash = 'a'")
                                .query(String.class)
                                .single())
                .isEqualTo("[0.6,-0.8]");
    }

    @Test
    void sameHashCoexistsForTwoModels() {
        repository.insert(SMALL, List.of(embedding("a")), now());
        repository.insert(OTHER, List.of(embedding("a")), now());
        repository.insert(
                new ModelInfo("fake", "small", 3),
                List.of(new NewEmbedding("a", new float[] {1f, 0f, 0f})),
                now());

        assertThat(count(jdbc, "chunk_embeddings")).isEqualTo(3);
    }

    @Test
    void nonFiniteVectorIsRejectedBeforeTheDatabase() {
        List<NewEmbedding> batch =
                List.of(embedding("a"), new NewEmbedding("b", new float[] {Float.NaN, 0f}));

        assertThatThrownBy(() -> repository.insert(SMALL, batch, now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count(jdbc, "chunk_embeddings")).isZero();
    }

    @Test
    void vectorWithAnotherDimensionIsRejectedBeforeTheDatabase() {
        List<NewEmbedding> batch = List.of(new NewEmbedding("a", new float[] {1f, 0f, 0f}));

        assertThatThrownBy(() -> repository.insert(SMALL, batch, now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("vector has 3 dimensions, expected 2");
        assertThat(count(jdbc, "chunk_embeddings")).isZero();
    }

    @Test
    void missingHashesAreTheSnapshotChunksWithoutAnEmbeddingOfTheModel() {
        insertContent(jdbc, projectId, "h1", "x");
        insertContent(jdbc, projectId, "h2", "y");
        insertContent(jdbc, projectId, "h3", "z");
        insertChunk(projectId, "h1", "lines-v1", 0, "a");
        insertChunk(projectId, "h1", "lines-v1", 1, "b");
        insertChunk(projectId, "h2", "lines-v1", 0, "b");
        insertChunk(projectId, "h2", "lines-v1", 1, "c");
        insertChunk(projectId, "h2", "lines-v1", 2, "d");
        insertChunk(projectId, "h1", "lines-v2", 0, "old-version");
        insertChunk(projectId, "h3", "lines-v1", 0, "other-snapshot");
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");
        insertFile(jdbc, snapshotId, projectId, "a.txt", "INDEXED", null, "h1");
        insertFile(jdbc, snapshotId, projectId, "copy-of-a.txt", "INDEXED", null, "h1");
        insertFile(jdbc, snapshotId, projectId, "b.txt", "INDEXED", null, "h2");
        insertFile(jdbc, snapshotId, projectId, "c.bin", "UNSUPPORTED", "BINARY", null);
        UUID otherSnapshot = insertSnapshot(jdbc, projectId, "BUILDING");
        insertFile(jdbc, otherSnapshot, projectId, "c.txt", "INDEXED", null, "h3");

        repository.insert(SMALL, List.of(embedding("a")), now());
        repository.insert(OTHER, List.of(embedding("b"), embedding("c")), now());
        repository.insert(
                new ModelInfo("fake", "small", 3),
                List.of(new NewEmbedding("d", new float[] {1f, 0f, 0f})),
                now());

        assertThat(repository.missingChunkHashes(snapshotId, SMALL)).containsExactly("b", "c", "d");
        assertThat(repository.missingChunkHashes(snapshotId, OTHER)).containsExactly("a", "d");
        assertThat(repository.missingChunkHashes(UUID.randomUUID(), SMALL)).isEmpty();
    }

    @Test
    void deleteOrphansKeepsHashesUsedByAnyProject() {
        UUID otherProject = insertZipProject(jdbc, insertWorkspace(jdbc));
        insertContent(jdbc, projectId, "h1", "x");
        insertContent(jdbc, otherProject, "h1", "x");
        insertChunk(projectId, "h1", "lines-v1", 0, "mine");
        insertChunk(otherProject, "h1", "lines-v1", 0, "theirs");
        repository.insert(SMALL, List.of(embedding("mine"), embedding("theirs")), now());
        repository.insert(OTHER, List.of(embedding("theirs"), embedding("gone")), now());
        repository.insert(SMALL, List.of(embedding("gone")), now());

        assertThat(repository.deleteOrphans()).isEqualTo(2);

        assertThat(
                        jdbc.sql(
                                        "SELECT chunk_hash || '/' || model FROM chunk_embeddings"
                                                + " ORDER BY 1")
                                .query(String.class)
                                .list())
                .containsExactly("mine/small", "theirs/other", "theirs/small");
        assertThat(repository.deleteOrphans()).isZero();
    }

    private void insertChunk(
            UUID project, String contentHash, String version, int ordinal, String chunkHash) {
        jdbc.sql(
                        """
                        INSERT INTO chunks (id, project_id, content_hash, chunking_version,
                                            ordinal, start_line, end_line, content, chunk_hash,
                                            token_count_estimate)
                        VALUES (:id, :projectId, :contentHash, :version, :ordinal, 1, 1, 'x',
                                :chunkHash, 1)
                        """)
                .param("id", UUID.randomUUID())
                .param("projectId", project)
                .param("contentHash", contentHash)
                .param("version", version)
                .param("ordinal", ordinal)
                .param("chunkHash", chunkHash)
                .update();
    }

    private static NewEmbedding embedding(String chunkHash) {
        return new NewEmbedding(chunkHash, new float[] {0.6f, -0.8f});
    }

    private static Instant now() {
        return Instant.now();
    }
}
