package com.codebasecopilot.importjob;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ImportJobRepository {

    private final JdbcClient jdbc;

    ImportJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean existsActiveByProject(UUID projectId) {
        return jdbc.sql(
                        """
                        SELECT EXISTS (
                            SELECT 1 FROM import_jobs
                            WHERE project_id = :projectId
                              AND status IN ('PENDING', 'EXTRACTING'))
                        """)
                .param("projectId", projectId)
                .query(Boolean.class)
                .single();
    }

    Map<UUID, ImportStatus> findStatuses(Collection<UUID> ids) {
        Map<UUID, ImportStatus> statuses = new HashMap<>();
        if (ids.isEmpty()) {
            return statuses;
        }
        jdbc.sql("SELECT id, status FROM import_jobs WHERE id IN (:ids)")
                .param("ids", ids)
                .query(
                        rs -> {
                            statuses.put(
                                    rs.getObject("id", UUID.class),
                                    ImportStatus.valueOf(rs.getString("status")));
                        });
        return statuses;
    }

    List<UUID> findIdsByProject(UUID projectId) {
        return jdbc.sql("SELECT id FROM import_jobs WHERE project_id = :projectId")
                .param("projectId", projectId)
                .query(UUID.class)
                .list();
    }
}
