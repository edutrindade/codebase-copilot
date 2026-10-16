package com.codebasecopilot.retrieval;

import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.shared.persistence.VectorLiteral;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Exact cosine search over the chunks of one snapshot, without an approximate index, so the
// snapshot filter never drops results (D-049). Reads the tables written by indexing (D-016).
@Repository
class VectorSearch {

    private final JdbcClient jdbc;

    VectorSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record SnapshotState(
            UUID projectId, String status, boolean pruned, @Nullable ModelInfo embedding) {}

    Optional<SnapshotState> findSnapshot(UUID snapshotId) {
        return jdbc.sql(
                        """
                        SELECT project_id, status, pruned_at IS NOT NULL AS pruned,
                               embedding_provider, embedding_model, embedding_dimensions
                        FROM snapshots WHERE id = :id
                        """)
                .param("id", snapshotId)
                .query(
                        (rs, rowNum) ->
                                new SnapshotState(
                                        rs.getObject("project_id", UUID.class),
                                        rs.getString("status"),
                                        rs.getBoolean("pruned"),
                                        rs.getString("embedding_provider") == null
                                                ? null
                                                : new ModelInfo(
                                                        rs.getString("embedding_provider"),
                                                        rs.getString("embedding_model"),
                                                        rs.getInt("embedding_dimensions"))))
                .optional();
    }

    // Ties are broken by first path, start line and id. Only vectors of the given model count
    // (D-048).
    static final String SEARCH =
            "WITH "
                    + SnapshotChunks.CANDIDATES
                    + """
                    , ranked AS (
                        SELECT k.id, k.first_path, k.start_line,
                               e.embedding <=> CAST(:query AS vector) AS distance,
                               1 - (e.embedding <=> CAST(:query AS vector)) AS score
                        FROM candidates k
                        JOIN chunk_embeddings e ON e.chunk_hash = k.chunk_hash
                                               AND e.provider = :provider
                                               AND e.model = :model
                                               AND e.dimensions = :dimensions
                        ORDER BY distance, k.first_path, k.start_line, k.id
                        LIMIT :limit
                    )
                    """
                    + SnapshotChunks.hits("r.distance, r.first_path, r.start_line, r.id");

    List<SearchHit> search(UUID snapshotId, ModelInfo model, float[] query, int limit) {
        if (query.length != model.dimensions()) {
            throw new IllegalArgumentException(
                    "query has " + query.length + " dimensions, expected " + model.dimensions());
        }
        return jdbc.sql(SEARCH)
                .param("snapshotId", snapshotId)
                .param("query", VectorLiteral.of(query))
                .param("provider", model.provider())
                .param("model", model.model())
                .param("dimensions", model.dimensions())
                .param("limit", limit)
                .query(SnapshotChunks::hit)
                .list();
    }
}
