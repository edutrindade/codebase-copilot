package com.codebasecopilot.project;

import com.codebasecopilot.shared.pagination.PageRequestParams;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
                               source_relative_path, created_at, updated_at
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

    long countByWorkspace(UUID workspaceId) {
        return jdbc.sql("SELECT count(*) FROM projects WHERE workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query(Long.class)
                .single();
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
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
