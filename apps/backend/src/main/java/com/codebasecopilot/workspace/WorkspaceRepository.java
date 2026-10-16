package com.codebasecopilot.workspace;

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
class WorkspaceRepository {

    // Owned by the projects table (V3); referenced by name only, see D-031.
    static final String PROJECTS_FK = "projects_workspace_id_fkey";

    private final JdbcAggregateTemplate aggregates;
    private final JdbcClient jdbc;

    WorkspaceRepository(JdbcAggregateTemplate aggregates, JdbcClient jdbc) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
    }

    // insert() instead of save(): ids are assigned before persisting (D-024).
    void insert(Workspace workspace) {
        aggregates.insert(workspace);
    }

    Optional<Workspace> findById(UUID id) {
        return Optional.ofNullable(aggregates.findById(id, Workspace.class));
    }

    List<Workspace> findPage(PageRequestParams params) {
        return jdbc.sql(
                        """
                        SELECT id, name, created_at, updated_at
                        FROM workspaces
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("limit", params.size())
                .param("offset", params.offset())
                .query(WorkspaceRepository::map)
                .list();
    }

    long count() {
        return jdbc.sql("SELECT count(*) FROM workspaces").query(Long.class).single();
    }

    boolean existsById(UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM workspaces WHERE id = :id)")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    boolean deleteById(UUID id) {
        return jdbc.sql("DELETE FROM workspaces WHERE id = :id").param("id", id).update() == 1;
    }

    private static Workspace map(ResultSet rs, int rowNum) throws SQLException {
        return new Workspace(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
