package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertProjectJob;
import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.IndexRows.count;
import static com.codebasecopilot.IndexRows.insertContent;
import static com.codebasecopilot.IndexRows.insertFile;
import static com.codebasecopilot.IndexRows.insertIndexJob;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static com.codebasecopilot.IndexRows.setActiveSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class IndexSchemaIT {

    @Autowired private JdbcClient jdbc;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        projectId = insertZipProject(jdbc, insertWorkspace(jdbc));
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                // status, counts, completed, pruned
                "BUILDING, null, true,  false",
                "BUILDING, null, false, true",
                "READY,    null, true,  false",
                "READY,    0,    false, false",
                "PARTIAL,  null, true,  false",
                "FAILED,   null, false, false",
                "UNKNOWN,  0,    true,  false"
            })
    void snapshotStateChecksRejectInconsistentRows(
            String status, Integer counts, boolean completed, boolean pruned) {
        assertCheckViolation(
                () ->
                        jdbc.sql(
                                        """
                                        INSERT INTO snapshots (id, project_id, status,
                                            settings_version, chunking_version, file_count,
                                            chunk_count, created_at, completed_at, pruned_at)
                                        VALUES (:id, :projectId, :status, 0, 'lines-v1',
                                            :counts, :counts, now(), :completedAt, :prunedAt)
                                        """)
                                .param("id", UUID.randomUUID())
                                .param("projectId", projectId)
                                .param("status", status)
                                .param("counts", counts)
                                .param("completedAt", completed ? OffsetDateTime.now() : null)
                                .param("prunedAt", pruned ? OffsetDateTime.now() : null)
                                .update());
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                // status, stage, heartbeat, errorCode, completed
                "PENDING,   null,      false, INDEX_IO_ERROR, false",
                "PENDING,   null,      false, null,           true",
                "RUNNING,   SCANNING,  false, null,           false",
                "COMPLETED, null,      true,  null,           false",
                "FAILED,    null,      true,  null,           true",
                "RUNNING,   UPLOADING, true,  null,           false",
                "UNKNOWN,   null,      false, null,           false"
            })
    void indexJobStateChecksRejectInconsistentRows(
            String status, String stage, boolean heartbeat, String errorCode, boolean completed) {
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");
        UUID jobId = insertProjectJob(jdbc, projectId, "INDEX", false);
        assertCheckViolation(
                () ->
                        jdbc.sql(
                                        """
                                        INSERT INTO index_jobs (id, project_id, snapshot_id,
                                            status, stage, heartbeat_at, error_code, created_at,
                                            completed_at)
                                        VALUES (:id, :projectId, :snapshotId, :status, :stage,
                                            :heartbeat, :errorCode, now(), :completedAt)
                                        """)
                                .param("id", jobId)
                                .param("projectId", projectId)
                                .param("snapshotId", snapshotId)
                                .param("status", status)
                                .param("stage", stage)
                                .param("heartbeat", heartbeat ? OffsetDateTime.now() : null)
                                .param("errorCode", errorCode)
                                .param("completedAt", completed ? OffsetDateTime.now() : null)
                                .update());
    }

    @Test
    void indexJobAcceptsTheEmbeddingStage() {
        UUID jobId =
                insertIndexJob(
                        jdbc, projectId, insertSnapshot(jdbc, projectId, "BUILDING"), "RUNNING");

        jdbc.sql("UPDATE index_jobs SET stage = 'EMBEDDING' WHERE id = :id")
                .param("id", jobId)
                .update();

        assertThat(
                        jdbc.sql("SELECT stage FROM index_jobs WHERE id = :id")
                                .param("id", jobId)
                                .query(String.class)
                                .single())
                .isEqualTo("EMBEDDING");
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                // provider, model, dimensions
                "openai, null,                   1536",
                "null,   text-embedding-3-small, 1536",
                "openai, text-embedding-3-small, null"
            })
    void snapshotEmbeddingMetadataIsAllOrNothing(
            String provider, String model, Integer dimensions) {
        UUID snapshotId = insertSnapshot(jdbc, projectId, "READY");

        assertCheckViolation(
                () ->
                        jdbc.sql(
                                        """
                                        UPDATE snapshots
                                        SET embedding_provider = :provider,
                                            embedding_model = :model,
                                            embedding_dimensions = :dimensions
                                        WHERE id = :id
                                        """)
                                .param("id", snapshotId)
                                .param("provider", provider)
                                .param("model", model)
                                .param("dimensions", dimensions)
                                .update());
    }

    @Test
    void embeddingColumnHasNoFixedDimension() {
        assertThat(
                        jdbc.sql(
                                        """
                                        SELECT format_type(atttypid, atttypmod)
                                        FROM pg_attribute
                                        WHERE attrelid = 'chunk_embeddings'::regclass
                                          AND attname = 'embedding'
                                        """)
                                .query(String.class)
                                .single())
                .isEqualTo("vector");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // dimensions, vector
                "3    | [1,2]",
                "2    | [1,2,3]",
                "0    | []",
                "2001 | [1]"
            })
    void embeddingChecksRejectInconsistentDimensions(int dimensions, String vector) {
        assertThatThrownBy(
                        () ->
                                jdbc.sql(
                                                """
                                                INSERT INTO chunk_embeddings (chunk_hash, provider,
                                                    model, dimensions, embedding, created_at)
                                                VALUES ('c', 'fake', 'm', :dimensions,
                                                    CAST(:vector AS vector), now())
                                                """)
                                        .param("dimensions", dimensions)
                                        .param("vector", vector)
                                        .update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void embeddingDimensionCheckUsesTheDeclaredConstraint() {
        assertThatThrownBy(
                        () ->
                                jdbc.sql(
                                                """
                                                INSERT INTO chunk_embeddings (chunk_hash, provider,
                                                    model, dimensions, embedding, created_at)
                                                VALUES ('c', 'fake', 'm', 3, '[1,2]', now())
                                                """)
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chunk_embeddings_dimensions_match");
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                // status, reason, withHash
                "INDEXED,     null,          false",
                "INDEXED,     BINARY,        true",
                "UNSUPPORTED, BINARY,        true",
                "UNSUPPORTED, null,          false",
                "UNSUPPORTED, READ_ERROR,    false",
                "FAILED,      BINARY,        false",
                "FAILED,      null,          false",
                "EXCLUDED,    null,          false"
            })
    void snapshotFileChecksRejectInconsistentRows(String status, String reason, boolean withHash) {
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");
        insertContent(jdbc, projectId, "h", "x");
        assertCheckViolation(
                () ->
                        insertFile(
                                jdbc,
                                snapshotId,
                                projectId,
                                "a.txt",
                                status,
                                reason,
                                withHash ? "h" : null));
    }

    @Test
    void allowsOneActiveIndexJobPerProject() {
        insertIndexJob(jdbc, projectId, insertSnapshot(jdbc, projectId, "READY"), "COMPLETED");
        insertIndexJob(jdbc, projectId, insertSnapshot(jdbc, projectId, "BUILDING"), "RUNNING");

        assertThatThrownBy(
                        () ->
                                insertIndexJob(
                                        jdbc,
                                        projectId,
                                        insertSnapshot(jdbc, projectId, "BUILDING"),
                                        "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("project_jobs_active_unique_idx");
    }

    @Test
    void indexJobNeedsAJobRegistryRow() {
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");

        assertThatThrownBy(
                        () ->
                                jdbc.sql(
                                                """
                                                INSERT INTO index_jobs (id, project_id,
                                                    snapshot_id, status, created_at)
                                                VALUES (:id, :projectId, :snapshotId, 'PENDING',
                                                    now())
                                                """)
                                        .param("id", UUID.randomUUID())
                                        .param("projectId", projectId)
                                        .param("snapshotId", snapshotId)
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("index_jobs_project_job_fkey");
    }

    @Test
    void activeSnapshotMustBelongToTheProject() {
        UUID other = insertZipProject(jdbc, insertWorkspace(jdbc));
        UUID otherSnapshot = insertSnapshot(jdbc, other, "READY");

        assertThatThrownBy(() -> setActiveSnapshot(jdbc, projectId, otherSnapshot))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("projects_active_snapshot_fkey");
    }

    @Test
    void fileContentMustExist() {
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");

        assertThatThrownBy(
                        () ->
                                insertFile(
                                        jdbc,
                                        snapshotId,
                                        projectId,
                                        "a.txt",
                                        "INDEXED",
                                        null,
                                        "missing"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("snapshot_files_content_fkey");
    }

    @Test
    void referencedContentCannotBeDeleted() {
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");
        insertContent(jdbc, projectId, "h", "x");
        insertFile(jdbc, snapshotId, projectId, "a.txt", "INDEXED", null, "h");

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM file_contents").update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("snapshot_files_content_fkey");
    }

    @Test
    void deletingProjectCascadesThroughEverything() {
        UUID snapshotId = insertSnapshot(jdbc, projectId, "READY");
        insertIndexJob(jdbc, projectId, snapshotId, "COMPLETED");
        insertContent(jdbc, projectId, "h", "x");
        insertFile(jdbc, snapshotId, projectId, "a.txt", "INDEXED", null, "h");
        jdbc.sql(
                        """
                        INSERT INTO chunks (id, project_id, content_hash, chunking_version,
                                            ordinal, start_line, end_line, content, chunk_hash,
                                            token_count_estimate)
                        VALUES (:id, :projectId, 'h', 'lines-v1', 0, 1, 1, 'x', 'c', 1)
                        """)
                .param("id", UUID.randomUUID())
                .param("projectId", projectId)
                .update();
        setActiveSnapshot(jdbc, projectId, snapshotId);

        jdbc.sql("DELETE FROM projects WHERE id = :id").param("id", projectId).update();

        for (String table :
                new String[] {
                    "snapshots",
                    "index_jobs",
                    "file_contents",
                    "snapshot_files",
                    "chunks",
                    "project_jobs"
                }) {
            assertThat(count(jdbc, table)).as(table).isZero();
        }
    }

    private static void assertCheckViolation(Runnable insert) {
        assertThatThrownBy(insert::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("violates check constraint");
    }
}
