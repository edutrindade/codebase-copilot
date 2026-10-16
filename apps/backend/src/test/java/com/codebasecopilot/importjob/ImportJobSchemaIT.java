package com.codebasecopilot.importjob;

import static com.codebasecopilot.ImportJobRows.insertJob;
import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.ImportJobRows.setCurrentImport;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class ImportJobSchemaIT {

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
                // status, uncompressed, entries, errorCode, completed
                "PENDING,    null, null, null,        true",
                "PENDING,    null, null, ZIP_INVALID, false",
                "EXTRACTING, null, null, null,        true",
                "COMPLETED,  200,  3,    null,        false",
                "COMPLETED,  null, 3,    null,        true",
                "COMPLETED,  200,  null, null,        true",
                "COMPLETED,  200,  3,    ZIP_INVALID, true",
                "REJECTED,   null, null, null,        true",
                "FAILED,     null, null, IO,          false",
                "UNKNOWN,    null, null, null,        false"
            })
    void stateChecksRejectInconsistentRows(
            String status,
            Long uncompressed,
            Integer entries,
            String errorCode,
            boolean completed) {
        assertThatThrownBy(
                        () ->
                                jdbc.sql(
                                                """
                                                INSERT INTO import_jobs (id, project_id, status,
                                                    compressed_bytes, uncompressed_bytes,
                                                    entry_count, error_code, created_at,
                                                    completed_at)
                                                VALUES (:id, :projectId, :status, 100,
                                                    :uncompressed, :entries, :errorCode, now(),
                                                    :completedAt)
                                                """)
                                        .param("id", UUID.randomUUID())
                                        .param("projectId", projectId)
                                        .param("status", status)
                                        .param("uncompressed", uncompressed)
                                        .param("entries", entries)
                                        .param("errorCode", errorCode)
                                        .param(
                                                "completedAt",
                                                completed ? OffsetDateTime.now() : null)
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("violates check constraint");
    }

    @Test
    void allowsOneActiveJobPerProject() {
        insertJob(jdbc, projectId, "COMPLETED");
        insertJob(jdbc, projectId, "FAILED");
        insertJob(jdbc, projectId, "PENDING");
        insertJob(jdbc, insertZipProject(jdbc, insertWorkspace(jdbc)), "EXTRACTING");

        assertThatThrownBy(() -> insertJob(jdbc, projectId, "EXTRACTING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("import_jobs_active_unique_idx");
    }

    @Test
    void currentImportOnlyForZipProjects() {
        UUID localId = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO projects (id, workspace_id, name, source_type,
                                              source_root_key, source_relative_path,
                                              created_at, updated_at)
                        SELECT :id, workspace_id, 'L', 'LOCAL_FOLDER', 'default', 'a', now(),
                               now()
                        FROM projects WHERE id = :projectId
                        """)
                .param("id", localId)
                .param("projectId", projectId)
                .update();
        UUID job = insertJob(jdbc, localId, "COMPLETED");

        assertThatThrownBy(() -> setCurrentImport(jdbc, localId, job))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("projects_current_import_zip_only");
    }

    @Test
    void currentImportMustBelongToTheProject() {
        UUID other = insertZipProject(jdbc, insertWorkspace(jdbc));
        UUID otherJob = insertJob(jdbc, other, "COMPLETED");

        assertThatThrownBy(() -> setCurrentImport(jdbc, projectId, otherJob))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("projects_current_import_fkey");
    }

    @Test
    void deletingProjectCascadesDespiteCurrentImport() {
        UUID job = insertJob(jdbc, projectId, "COMPLETED");
        insertJob(jdbc, projectId, "FAILED");
        setCurrentImport(jdbc, projectId, job);

        jdbc.sql("DELETE FROM projects WHERE id = :id").param("id", projectId).update();

        assertThat(jdbc.sql("SELECT count(*) FROM import_jobs").query(Long.class).single())
                .isZero();
    }

    @Test
    void deletingCurrentImportClearsOnlyTheReference() {
        UUID job = insertJob(jdbc, projectId, "COMPLETED");
        setCurrentImport(jdbc, projectId, job);

        jdbc.sql("DELETE FROM import_jobs WHERE id = :id").param("id", job).update();

        assertThat(
                        jdbc.sql("SELECT current_import_job_id FROM projects WHERE id = :id")
                                .param("id", projectId)
                                .query(UUID.class)
                                .optional())
                .isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM projects").query(Long.class).single())
                .isEqualTo(1);
    }
}
