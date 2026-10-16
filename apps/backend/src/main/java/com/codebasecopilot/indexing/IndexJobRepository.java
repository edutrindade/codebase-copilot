package com.codebasecopilot.indexing;

import com.codebasecopilot.shared.pagination.PageRequestParams;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Every transition after the claim is conditional on the attempt, so a worker whose lease
// expired cannot overwrite the worker that took the job over (D-040).
@Repository
class IndexJobRepository {

    private static final String COLUMNS =
            """
            id, project_id, snapshot_id, status, stage, progress_total, progress_completed,
            attempt_count, error_code, created_at, started_at, completed_at
            """;

    private final JdbcClient jdbc;

    IndexJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insertPending(UUID id, UUID projectId, UUID snapshotId, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO index_jobs (id, project_id, snapshot_id, status, created_at)
                        VALUES (:id, :projectId, :snapshotId, 'PENDING', :now)
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("snapshotId", snapshotId)
                .param("now", Timestamps.of(now))
                .update();
    }

    Optional<IndexJob> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM index_jobs WHERE id = :id")
                .param("id", id)
                .query(IndexJobRepository::map)
                .optional();
    }

    List<IndexJob> findPageByProject(UUID projectId, PageRequestParams params) {
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + """
                                FROM index_jobs
                                WHERE project_id = :projectId
                                ORDER BY created_at DESC, id DESC
                                LIMIT :limit OFFSET :offset
                                """)
                .param("projectId", projectId)
                .param("limit", params.size())
                .param("offset", params.offset())
                .query(IndexJobRepository::map)
                .list();
    }

    List<IndexJob> findActiveByProjects(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(
                        "SELECT "
                                + COLUMNS
                                + """
                                FROM index_jobs
                                WHERE project_id IN (:ids) AND status IN ('PENDING', 'RUNNING')
                                """)
                .param("ids", projectIds)
                .query(IndexJobRepository::map)
                .list();
    }

    long countByProject(UUID projectId) {
        return jdbc.sql("SELECT count(*) FROM index_jobs WHERE project_id = :projectId")
                .param("projectId", projectId)
                .query(Long.class)
                .single();
    }

    // Must run in a transaction: the row stays locked until the claim commits.
    Optional<Claim> lockNext(Instant staleBefore) {
        return jdbc.sql(
                        """
                        SELECT id, project_id, snapshot_id, attempt_count
                        FROM index_jobs
                        WHERE status = 'PENDING'
                           OR (status = 'RUNNING' AND heartbeat_at < :staleBefore)
                        ORDER BY created_at, id
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("staleBefore", Timestamps.of(staleBefore))
                .query(
                        (rs, rowNum) ->
                                new Claim(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("project_id", UUID.class),
                                        rs.getObject("snapshot_id", UUID.class),
                                        rs.getInt("attempt_count")))
                .optional();
    }

    boolean start(Claim locked, Instant now) {
        return jdbc.sql(
                                """
                                UPDATE index_jobs
                                SET status = 'RUNNING', attempt_count = attempt_count + 1,
                                    heartbeat_at = :now, started_at = coalesce(started_at, :now),
                                    stage = NULL, progress_total = NULL, progress_completed = NULL
                                WHERE id = :id AND attempt_count = :attempt
                                  AND status IN ('PENDING', 'RUNNING')
                                """)
                        .param("id", locked.jobId())
                        .param("attempt", locked.attempt())
                        .param("now", Timestamps.of(now))
                        .update()
                == 1;
    }

    boolean heartbeat(
            Claim claim,
            IndexStage stage,
            @Nullable Integer total,
            @Nullable Integer completed,
            Instant now) {
        return jdbc.sql(
                                """
                                UPDATE index_jobs
                                SET heartbeat_at = :now, stage = :stage,
                                    progress_total = :total, progress_completed = :completed
                                WHERE id = :id AND attempt_count = :attempt
                                  AND status = 'RUNNING'
                                """)
                        .param("id", claim.jobId())
                        .param("attempt", claim.attempt())
                        .param("stage", stage.name())
                        .param("total", total)
                        .param("completed", completed)
                        .param("now", Timestamps.of(now))
                        .update()
                == 1;
    }

    boolean complete(Claim claim, Instant now) {
        return jdbc.sql(
                                """
                                UPDATE index_jobs
                                SET status = 'COMPLETED', completed_at = :now
                                WHERE id = :id AND attempt_count = :attempt
                                  AND status = 'RUNNING'
                                """)
                        .param("id", claim.jobId())
                        .param("attempt", claim.attempt())
                        .param("now", Timestamps.of(now))
                        .update()
                == 1;
    }

    boolean fail(Claim claim, IndexErrorCode code, Instant now) {
        return jdbc.sql(
                                """
                                UPDATE index_jobs
                                SET status = 'FAILED', error_code = :code, completed_at = :now
                                WHERE id = :id AND attempt_count = :attempt
                                  AND status IN ('PENDING', 'RUNNING')
                                """)
                        .param("id", claim.jobId())
                        .param("attempt", claim.attempt())
                        .param("code", code.name())
                        .param("now", Timestamps.of(now))
                        .update()
                == 1;
    }

    // Back to the queue right away, keeping the attempt count.
    boolean requeue(Claim claim) {
        return jdbc.sql(
                                """
                                UPDATE index_jobs
                                SET status = 'PENDING', heartbeat_at = NULL, stage = NULL
                                WHERE id = :id AND attempt_count = :attempt
                                  AND status = 'RUNNING'
                                """)
                        .param("id", claim.jobId())
                        .param("attempt", claim.attempt())
                        .update()
                == 1;
    }

    private static IndexJob map(ResultSet rs, int rowNum) throws SQLException {
        String stage = rs.getString("stage");
        String errorCode = rs.getString("error_code");
        return new IndexJob(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("snapshot_id", UUID.class),
                IndexJobStatus.valueOf(rs.getString("status")),
                stage == null ? null : IndexStage.valueOf(stage),
                rs.getObject("progress_total", Integer.class),
                rs.getObject("progress_completed", Integer.class),
                rs.getInt("attempt_count"),
                errorCode == null ? null : IndexErrorCode.valueOf(errorCode),
                Objects.requireNonNull(Timestamps.read(rs, "created_at")),
                Timestamps.read(rs, "started_at"),
                Timestamps.read(rs, "completed_at"));
    }
}
