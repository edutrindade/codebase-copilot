package com.codebasecopilot.project;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProjectJobRepository {

    static final String ACTIVE_UNIQUE = "project_jobs_active_unique_idx";
    static final String PROJECT_FK = "project_jobs_project_id_fkey";

    private final JdbcClient jdbc;

    ProjectJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insertActive(UUID id, UUID projectId, JobKind kind, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO project_jobs (id, project_id, kind, active, created_at)
                        VALUES (:id, :projectId, :kind, true, :now)
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("kind", kind.name())
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }

    void deactivate(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql("UPDATE project_jobs SET active = false WHERE id IN (:ids) AND active")
                .param("ids", ids)
                .update();
    }

    boolean existsActive(UUID projectId) {
        return jdbc.sql(
                        """
                        SELECT EXISTS (
                            SELECT 1 FROM project_jobs WHERE project_id = :projectId AND active)
                        """)
                .param("projectId", projectId)
                .query(Boolean.class)
                .single();
    }
}
