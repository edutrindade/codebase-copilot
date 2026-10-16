package com.codebasecopilot.project;

import static com.codebasecopilot.ImportJobRows.insertProjectJob;
import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class ProjectJobSchemaIT {

    @Autowired private JdbcClient jdbc;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        projectId = insertZipProject(jdbc, insertWorkspace(jdbc));
    }

    @Test
    void allowsOneActiveJobOfAnyKindPerProject() {
        insertProjectJob(jdbc, projectId, "IMPORT", false);
        insertProjectJob(jdbc, projectId, "INDEX", false);
        insertProjectJob(jdbc, projectId, "IMPORT", true);
        insertProjectJob(jdbc, insertZipProject(jdbc, insertWorkspace(jdbc)), "INDEX", true);

        assertThatThrownBy(() -> insertProjectJob(jdbc, projectId, "INDEX", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("project_jobs_active_unique_idx");
    }

    @Test
    void rejectsUnknownKind() {
        assertThatThrownBy(() -> insertProjectJob(jdbc, projectId, "EXPORT", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("project_jobs_kind_valid");
    }

    @Test
    void requiresExistingProject() {
        assertThatThrownBy(() -> insertProjectJob(jdbc, UUID.randomUUID(), "INDEX", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("project_jobs_project_id_fkey");
    }
}
