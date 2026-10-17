package com.codebasecopilot.retrieval;

import com.codebasecopilot.providers.ModelInfo;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// What the search checks about a snapshot before querying it (D-060).
@Repository
class SearchSnapshots {

    private final JdbcClient jdbc;

    SearchSnapshots(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record SnapshotState(
            UUID projectId,
            String status,
            boolean pruned,
            @Nullable ModelInfo embedding,
            @Nullable String lexicalVersion) {}

    Optional<SnapshotState> find(UUID snapshotId) {
        return jdbc.sql(
                        """
                        SELECT project_id, status, pruned_at IS NOT NULL AS pruned,
                               embedding_provider, embedding_model, embedding_dimensions,
                               lexical_version
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
                                                        rs.getInt("embedding_dimensions")),
                                        rs.getString("lexical_version")))
                .optional();
    }

    record ActiveFile(
            @Nullable UUID activeSnapshotId, boolean present, @Nullable String contentHash) {}

    Optional<ActiveFile> findActiveFile(UUID projectId, String relativePath) {
        return jdbc.sql(
                        """
                        SELECT p.active_snapshot_id, f.relative_path IS NOT NULL AS present,
                               f.content_hash
                        FROM projects p
                        LEFT JOIN snapshot_files f
                               ON f.snapshot_id = p.active_snapshot_id
                              AND f.relative_path = :path
                        WHERE p.id = :projectId
                        """)
                .param("projectId", projectId)
                .param("path", relativePath)
                .query(
                        (rs, rowNum) ->
                                new ActiveFile(
                                        rs.getObject("active_snapshot_id", UUID.class),
                                        rs.getBoolean("present"),
                                        rs.getString("content_hash")))
                .optional();
    }
}
