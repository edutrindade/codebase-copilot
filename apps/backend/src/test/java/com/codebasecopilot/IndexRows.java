package com.codebasecopilot;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

// Snapshot and index job rows inserted with SQL, for states the API does not create directly.
public final class IndexRows {

    private IndexRows() {}

    // Fills the columns that snapshots_state_consistency requires for the status.
    public static UUID insertSnapshot(JdbcClient jdbc, UUID projectId, String status) {
        UUID id = UUID.randomUUID();
        boolean done = !status.equals("BUILDING");
        boolean usable = status.equals("READY") || status.equals("PARTIAL");
        jdbc.sql(
                        """
                        INSERT INTO snapshots (id, project_id, status, settings_version,
                                               chunking_version, file_count, chunk_count,
                                               created_at, completed_at)
                        VALUES (:id, :projectId, :status, 0, 'lines-v1', :count, :count, now(),
                                :completedAt)
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("status", status)
                .param("count", usable ? 0 : null)
                .param("completedAt", done ? OffsetDateTime.now() : null)
                .update();
        return id;
    }

    public static UUID insertIndexJob(
            JdbcClient jdbc, UUID projectId, UUID snapshotId, String status) {
        boolean active = status.equals("PENDING") || status.equals("RUNNING");
        UUID id = ImportJobRows.insertProjectJob(jdbc, projectId, "INDEX", active);
        jdbc.sql(
                        """
                        INSERT INTO index_jobs (id, project_id, snapshot_id, status,
                                                heartbeat_at, error_code, created_at,
                                                completed_at)
                        VALUES (:id, :projectId, :snapshotId, :status, :heartbeat, :errorCode,
                                now(), :completedAt)
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("snapshotId", snapshotId)
                .param("status", status)
                .param("heartbeat", status.equals("RUNNING") ? OffsetDateTime.now() : null)
                .param("errorCode", status.equals("FAILED") ? "INDEX_IO_ERROR" : null)
                .param("completedAt", active ? null : OffsetDateTime.now())
                .update();
        return id;
    }

    public static void insertContent(JdbcClient jdbc, UUID projectId, String hash, String content) {
        jdbc.sql(
                        """
                        INSERT INTO file_contents (project_id, content_hash, content, size_bytes,
                                                   line_count, created_at)
                        VALUES (:projectId, :hash, :content, 1, 1, now())
                        """)
                .param("projectId", projectId)
                .param("hash", hash)
                .param("content", content)
                .update();
    }

    public static void insertFile(
            JdbcClient jdbc,
            UUID snapshotId,
            UUID projectId,
            String path,
            String status,
            String reason,
            String hash) {
        jdbc.sql(
                        """
                        INSERT INTO snapshot_files (snapshot_id, project_id, relative_path,
                                                    status, reason, content_hash, language,
                                                    size_bytes)
                        VALUES (:snapshotId, :projectId, :path, :status, :reason, :hash, 'text',
                                1)
                        """)
                .param("snapshotId", snapshotId)
                .param("projectId", projectId)
                .param("path", path)
                .param("status", status)
                .param("reason", reason)
                .param("hash", hash)
                .update();
    }

    public static void setActiveSnapshot(JdbcClient jdbc, UUID projectId, UUID snapshotId) {
        jdbc.sql("UPDATE projects SET active_snapshot_id = :snapshotId WHERE id = :id")
                .param("id", projectId)
                .param("snapshotId", snapshotId)
                .update();
    }

    public static long count(JdbcClient jdbc, String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
