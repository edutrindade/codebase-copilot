package com.codebasecopilot;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

// Rows inserted with SQL, so tests can set up states the API does not create yet.
public final class ImportJobRows {

    private ImportJobRows() {}

    public static UUID insertWorkspace(JdbcClient jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO workspaces (id, name, created_at, updated_at)
                        VALUES (:id, 'W', now(), now())
                        """)
                .param("id", id)
                .update();
        return id;
    }

    public static UUID insertZipProject(JdbcClient jdbc, UUID workspaceId) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO projects (id, workspace_id, name, source_type, created_at,
                                              updated_at)
                        VALUES (:id, :workspaceId, 'P', 'ZIP', now(), now())
                        """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .update();
        return id;
    }

    // Fills the columns that import_jobs_state_consistency requires for the status.
    public static UUID insertJob(JdbcClient jdbc, UUID projectId, String status) {
        boolean active = status.equals("PENDING") || status.equals("EXTRACTING");
        boolean completed = status.equals("COMPLETED");
        UUID id = insertProjectJob(jdbc, projectId, "IMPORT", active);
        jdbc.sql(
                        """
                        INSERT INTO import_jobs (id, project_id, status, compressed_bytes,
                                                 uncompressed_bytes, entry_count, error_code,
                                                 created_at, completed_at)
                        VALUES (:id, :projectId, :status, 100, :uncompressed, :entries,
                                :errorCode, now(), :completedAt)
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("status", status)
                .param("uncompressed", completed ? 200L : null)
                .param("entries", completed ? 3 : null)
                .param("errorCode", active || completed ? null : "ZIP_INVALID")
                .param("completedAt", active ? null : OffsetDateTime.now())
                .update();
        return id;
    }

    public static UUID insertProjectJob(
            JdbcClient jdbc, UUID projectId, String kind, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO project_jobs (id, project_id, kind, active, created_at)
                        VALUES (:id, :projectId, :kind, :active, now())
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("kind", kind)
                .param("active", active)
                .update();
        return id;
    }

    public static boolean isJobActive(JdbcClient jdbc, UUID jobId) {
        return jdbc.sql("SELECT active FROM project_jobs WHERE id = :id")
                .param("id", jobId)
                .query(Boolean.class)
                .single();
    }

    public static void setCurrentImport(JdbcClient jdbc, UUID projectId, UUID importJobId) {
        jdbc.sql("UPDATE projects SET current_import_job_id = :importId WHERE id = :id")
                .param("id", projectId)
                .param("importId", importJobId)
                .update();
    }
}
