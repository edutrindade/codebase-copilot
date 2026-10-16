package com.codebasecopilot.importjob;

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
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// State transitions are conditional on the current status, so a job finishes only once.
@Repository
class ImportJobRepository {

    static final String ACTIVE_UNIQUE = "import_jobs_active_unique_idx";
    static final String PROJECT_FK = "import_jobs_project_id_fkey";

    private static final String COLUMNS =
            """
            id, project_id, status, compressed_bytes, uncompressed_bytes, entry_count,
            error_code, created_at, completed_at
            """;

    private final JdbcClient jdbc;

    ImportJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insertPending(UUID id, UUID projectId, long compressedBytes, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO import_jobs (id, project_id, status, compressed_bytes,
                                                 created_at)
                        VALUES (:id, :projectId, 'PENDING', :compressedBytes, :now)
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("compressedBytes", compressedBytes)
                .param("now", timestamp(now))
                .update();
    }

    Optional<ImportJob> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM import_jobs WHERE id = :id")
                .param("id", id)
                .query(ImportJobRepository::map)
                .optional();
    }

    List<ImportJob> findPageByProject(UUID projectId, PageRequestParams params) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + """
                                FROM import_jobs
                                WHERE project_id = :projectId
                                ORDER BY created_at DESC, id DESC
                                LIMIT :limit OFFSET :offset
                                """)
                .param("projectId", projectId)
                .param("limit", params.size())
                .param("offset", params.offset())
                .query(ImportJobRepository::map)
                .list();
    }

    long countByProject(UUID projectId) {
        return jdbc.sql("SELECT count(*) FROM import_jobs WHERE project_id = :projectId")
                .param("projectId", projectId)
                .query(Long.class)
                .single();
    }

    boolean markExtracting(UUID id) {
        return jdbc.sql(
                                """
                                UPDATE import_jobs SET status = 'EXTRACTING'
                                WHERE id = :id AND status = 'PENDING'
                                """)
                        .param("id", id)
                        .update()
                == 1;
    }

    boolean markCompleted(UUID id, long uncompressedBytes, int entryCount, Instant now) {
        return jdbc.sql(
                                """
                                UPDATE import_jobs
                                SET status = 'COMPLETED', uncompressed_bytes = :bytes,
                                    entry_count = :entries, completed_at = :now
                                WHERE id = :id AND status = 'EXTRACTING'
                                """)
                        .param("id", id)
                        .param("bytes", uncompressedBytes)
                        .param("entries", entryCount)
                        .param("now", timestamp(now))
                        .update()
                == 1;
    }

    boolean markFinished(UUID id, ImportStatus status, ImportErrorCode code, Instant now) {
        return jdbc.sql(
                                """
                                UPDATE import_jobs
                                SET status = :status, error_code = :code, completed_at = :now
                                WHERE id = :id AND status IN ('PENDING', 'EXTRACTING')
                                """)
                        .param("id", id)
                        .param("status", status.name())
                        .param("code", code.name())
                        .param("now", timestamp(now))
                        .update()
                == 1;
    }

    List<UUID> failAllActive(ImportErrorCode code, Instant now) {
        return jdbc.sql(
                        """
                        UPDATE import_jobs
                        SET status = 'FAILED', error_code = :code, completed_at = :now
                        WHERE status IN ('PENDING', 'EXTRACTING')
                        RETURNING id
                        """)
                .param("code", code.name())
                .param("now", timestamp(now))
                .query(UUID.class)
                .list();
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

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static ImportJob map(ResultSet rs, int rowNum) throws SQLException {
        String errorCode = rs.getString("error_code");
        return new ImportJob(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                ImportStatus.valueOf(rs.getString("status")),
                rs.getLong("compressed_bytes"),
                rs.getObject("uncompressed_bytes", Long.class),
                rs.getObject("entry_count", Integer.class),
                errorCode == null ? null : ImportErrorCode.valueOf(errorCode),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                instant(rs, "completed_at"));
    }
}
