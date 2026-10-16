package com.codebasecopilot.retrieval;

import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.shared.persistence.VectorLiteral;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
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

    record Hit(
            UUID chunkId,
            List<String> paths,
            int startLine,
            int endLine,
            String content,
            double score) {}

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

    // One row per chunk; a chunk shared by several files of the snapshot lists every path, in
    // byte order (D-039). Ties are broken by first path, start line and id. Only vectors of the
    // given model count (D-048), and a pruned snapshot has no files left to match.
    static final String SEARCH =
            """
            WITH candidates AS (
                SELECT c.id, c.chunk_hash, c.start_line,
                       min(f.relative_path COLLATE "C") AS first_path
                FROM snapshots s
                JOIN snapshot_files f ON f.snapshot_id = s.id
                JOIN chunks c ON c.project_id = f.project_id
                             AND c.content_hash = f.content_hash
                             AND c.chunking_version = s.chunking_version
                WHERE s.id = :snapshotId
                  AND f.content_hash IS NOT NULL
                GROUP BY c.id, c.chunk_hash, c.start_line
            ),
            ranked AS (
                SELECT k.id, k.first_path, k.start_line,
                       e.embedding <=> CAST(:query AS vector) AS distance
                FROM candidates k
                JOIN chunk_embeddings e ON e.chunk_hash = k.chunk_hash
                                       AND e.provider = :provider
                                       AND e.model = :model
                                       AND e.dimensions = :dimensions
                ORDER BY distance, k.first_path, k.start_line, k.id
                LIMIT :limit
            )
            SELECT r.id, c.start_line, c.end_line, c.content, 1 - r.distance AS score,
                   ARRAY(SELECT f.relative_path
                         FROM snapshot_files f
                         WHERE f.snapshot_id = :snapshotId
                           AND f.project_id = c.project_id
                           AND f.content_hash = c.content_hash
                         ORDER BY f.relative_path COLLATE "C") AS paths
            FROM ranked r
            JOIN chunks c ON c.id = r.id
            ORDER BY r.distance, r.first_path, r.start_line, r.id
            """;

    List<Hit> search(UUID snapshotId, ModelInfo model, float[] query, int limit) {
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
                .query(VectorSearch::hit)
                .list();
    }

    private static Hit hit(ResultSet rs, int rowNum) throws SQLException {
        Array paths = rs.getArray("paths");
        try {
            return new Hit(
                    rs.getObject("id", UUID.class),
                    Arrays.asList((String[]) paths.getArray()),
                    rs.getInt("start_line"),
                    rs.getInt("end_line"),
                    rs.getString("content"),
                    rs.getDouble("score"));
        } finally {
            paths.free();
        }
    }
}
