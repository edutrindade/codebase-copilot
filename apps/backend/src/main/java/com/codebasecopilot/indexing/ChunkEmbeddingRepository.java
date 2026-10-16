package com.codebasecopilot.indexing;

import com.codebasecopilot.providers.ModelInfo;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Embeddings are global and keyed by chunk hash and model, so chunks with the same text share
// one row across snapshots and projects (D-048).
@Repository
class ChunkEmbeddingRepository {

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate batch;

    ChunkEmbeddingRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    record NewEmbedding(String chunkHash, float[] vector) {}

    // Chunks of the snapshot are its files' contents chunked with the snapshot's version
    // (D-039). Joins use chunks_natural_key and the chunk_embeddings primary key.
    static final String MISSING_CHUNK_HASHES =
            """
            SELECT DISTINCT c.chunk_hash
            FROM snapshots s
            JOIN snapshot_files f ON f.snapshot_id = s.id
            JOIN chunks c ON c.project_id = f.project_id
                         AND c.content_hash = f.content_hash
                         AND c.chunking_version = s.chunking_version
            WHERE s.id = :snapshotId
              AND f.content_hash IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM chunk_embeddings e
                              WHERE e.chunk_hash = c.chunk_hash
                                AND e.provider = :provider
                                AND e.model = :model
                                AND e.dimensions = :dimensions)
            ORDER BY c.chunk_hash
            """;

    List<String> missingChunkHashes(UUID snapshotId, ModelInfo model) {
        return jdbc.sql(MISSING_CHUNK_HASHES)
                .param("snapshotId", snapshotId)
                .param("provider", model.provider())
                .param("model", model.model())
                .param("dimensions", model.dimensions())
                .query(String.class)
                .list();
    }

    // The hash covers the chunking version and the text, so any chunk with it has the same text.
    Map<String, String> chunkTexts(UUID projectId, Collection<String> chunkHashes) {
        if (chunkHashes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> texts = new HashMap<>();
        jdbc.sql(
                        """
                        SELECT DISTINCT ON (chunk_hash) chunk_hash, content
                        FROM chunks
                        WHERE project_id = :projectId AND chunk_hash IN (:hashes)
                        """)
                .param("projectId", projectId)
                .param("hashes", chunkHashes)
                .query(
                        rs -> {
                            texts.put(rs.getString("chunk_hash"), rs.getString("content"));
                        });
        return texts;
    }

    // Returns how many rows were actually inserted; rows already cached are skipped.
    int insert(ModelInfo model, List<NewEmbedding> embeddings, Instant now) {
        if (embeddings.isEmpty()) {
            return 0;
        }
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        SqlParameterSource[] rows =
                embeddings.stream()
                        .map(
                                embedding -> {
                                    if (embedding.vector().length != model.dimensions()) {
                                        throw new IllegalArgumentException(
                                                "vector has "
                                                        + embedding.vector().length
                                                        + " dimensions, expected "
                                                        + model.dimensions());
                                    }
                                    return new MapSqlParameterSource()
                                            .addValue("chunkHash", embedding.chunkHash())
                                            .addValue("provider", model.provider())
                                            .addValue("model", model.model())
                                            .addValue("dimensions", model.dimensions())
                                            .addValue(
                                                    "embedding",
                                                    VectorLiteral.of(embedding.vector()))
                                            .addValue("createdAt", createdAt);
                                })
                        .toArray(SqlParameterSource[]::new);
        int[] counts =
                batch.batchUpdate(
                        """
                        INSERT INTO chunk_embeddings (chunk_hash, provider, model, dimensions,
                                                      embedding, created_at)
                        VALUES (:chunkHash, :provider, :model, :dimensions,
                                CAST(:embedding AS vector), :createdAt)
                        ON CONFLICT DO NOTHING
                        """,
                        rows);
        int inserted = 0;
        for (int count : counts) {
            if (count < 0) {
                throw new IllegalStateException("driver did not report batch row counts");
            }
            inserted += count;
        }
        return inserted;
    }

    // Embeddings whose text no longer exists in any chunk of any project (D-052).
    int deleteOrphans() {
        return jdbc.sql(
                        """
                        DELETE FROM chunk_embeddings e
                        WHERE NOT EXISTS (SELECT 1 FROM chunks c
                                          WHERE c.chunk_hash = e.chunk_hash)
                        """)
                .update();
    }
}
