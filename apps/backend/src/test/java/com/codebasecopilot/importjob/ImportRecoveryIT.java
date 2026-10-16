package com.codebasecopilot.importjob;

import static com.codebasecopilot.ImportJobRows.insertJob;
import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.ImportJobRows.isJobActive;
import static com.codebasecopilot.ImportJobRows.setCurrentImport;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.shared.datadir.DataDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class ImportRecoveryIT {

    @Autowired private ImportRecovery recovery;
    @Autowired private JdbcClient jdbc;
    @Autowired private DataDirectory dataDirectory;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void failsInterruptedJobsAndRemovesFoldersThatAreNotCurrent() throws IOException {
        UUID workspace = insertWorkspace(jdbc);
        UUID busy = insertZipProject(jdbc, workspace);
        UUID other = insertZipProject(jdbc, workspace);
        UUID pending = insertJob(jdbc, busy, "PENDING");
        UUID extracting = insertJob(jdbc, other, "EXTRACTING");
        UUID current = insertJob(jdbc, busy, "COMPLETED");
        UUID superseded = insertJob(jdbc, other, "COMPLETED");
        setCurrentImport(jdbc, busy, current);
        for (UUID id : new UUID[] {pending, extracting, current, superseded, UUID.randomUUID()}) {
            Files.createDirectories(dataDirectory.importDir(id).resolve("source"));
        }
        Path junk = Files.createDirectories(dataDirectory.importsDir().resolve("not-an-id"));

        recovery.recover();

        assertThat(status(pending)).isEqualTo("FAILED/IMPORT_INTERRUPTED");
        assertThat(status(extracting)).isEqualTo("FAILED/IMPORT_INTERRUPTED");
        assertThat(status(current)).isEqualTo("COMPLETED/null");
        assertThat(isJobActive(jdbc, pending)).isFalse();
        assertThat(isJobActive(jdbc, extracting)).isFalse();
        assertThat(dataDirectory.importDir(current)).isDirectory();
        try (var entries = Files.list(dataDirectory.importsDir())) {
            assertThat(entries).containsExactly(dataDirectory.importDir(current));
        }
        assertThat(junk).doesNotExist();
    }

    private String status(UUID id) {
        return jdbc.sql(
                        "SELECT status || '/' || coalesce(error_code, 'null') FROM import_jobs WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }
}
