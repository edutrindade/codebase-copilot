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
}
