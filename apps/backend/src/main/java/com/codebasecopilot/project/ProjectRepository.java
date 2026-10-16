package com.codebasecopilot.project;

import com.codebasecopilot.shared.pagination.PageRequestParams;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProjectRepository {

    static final String WORKSPACE_FK = "projects_workspace_id_fkey";
    static final String LOCAL_FOLDER_UNIQUE = "projects_local_folder_unique_idx";

    private final JdbcAggregateTemplate aggregates;
    private final JdbcClient jdbc;

    ProjectRepository(JdbcAggregateTemplate aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    // insert() instead of save(): ids are assigned before persisting (D-024).
    void insert(Project project) {
        aggregates.insert(project);
    }

    Optional<Project> findById(UUID id) {
        return Optional.ofNullable(aggregates.findById(id, Project.class));
    }

    List<Project> findPageByWorkspace(UUID workspaceId, PageRequestParams params) {
        return jdbc.sql(
                        """
                        SELECT id, workspace_id, name, source_type, source_root_key,
                               source_relative_path, current_import_job_id, created_at,
                               updated_at
                        FROM projects
                        WHERE workspace_id = :workspaceId
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("workspaceId", workspaceId)
                .param("limit", params.size())
                .param("offset", params.offset())
                .query(ProjectRepository::map)
                .list();
    }

    // Blocks concurrent uploads, whose insert into import_jobs takes a KEY SHARE lock here.
    Optional<Project> findByIdForUpdate(UUID id) {
        return jdbc.sql(
                        """
                        SELECT id, workspace_id, name, source_type, source_root_key,
                               source_relative_path, current_import_job_id, created_at,
                               updated_at
                        FROM projects
                        WHERE id = :id
                        FOR UPDATE
                        """)
                .param("id", id)
                .query(ProjectRepository::map)
                .optional();
    }

    void updateCurrentImport(UUID id, UUID importJobId, Instant now) {
        jdbc.sql(
                        """
                        UPDATE projects
                        SET current_import_job_id = :importJobId, updated_at = :now
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("importJobId", importJobId)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }

    Set<UUID> findCurrentImportIds(Collection<UUID> importJobIds) {
        if (importJobIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(
                jdbc.sql(
                                """
                                SELECT current_import_job_id FROM projects
                                WHERE current_import_job_id IN (:ids)
                                """)
                        .param("ids", importJobIds)
                        .query(UUID.class)
                        .list());
    }

    long countByWorkspace(UUID workspaceId) {
        return jdbc.sql("SELECT count(*) FROM projects WHERE workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query(Long.class)
                .single();
    }

    Map<UUID, Long> countByWorkspaces(Collection<UUID> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        jdbc.sql(
                        """
                        SELECT workspace_id, count(*) AS total
                        FROM projects
                        WHERE workspace_id IN (:workspaceIds)
                        GROUP BY workspace_id
                        """)
                .param("workspaceIds", workspaceIds)
                .query(
                        rs -> {
                            counts.put(
                                    rs.getObject("workspace_id", UUID.class), rs.getLong("total"));
                        });
        return counts;
    }

    boolean deleteById(UUID id) {
        return jdbc.sql("DELETE FROM projects WHERE id = :id").param("id", id).update() == 1;
    }

    private static Project map(ResultSet rs, int rowNum) throws SQLException {
        return new Project(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("name"),
                SourceType.valueOf(rs.getString("source_type")),
                rs.getString("source_root_key"),
                rs.getString("source_relative_path"),
                rs.getObject("current_import_job_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
