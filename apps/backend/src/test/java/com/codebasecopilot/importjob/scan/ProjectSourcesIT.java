package com.codebasecopilot.importjob.scan;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// Trees described in TestFileSystemInitializer.
@IntegrationTest
class ProjectSourcesIT {

    @Autowired private ProjectSources sources;
    @Autowired private JdbcClient jdbc;

    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        workspaceId = insertWorkspace(jdbc);
    }

    @Test
    void scanListsIncludedFilesSortedAndMatchingTheTotals() throws IOException {
        UUID projectId = localProject("work", "inventory-repo");

        ProjectSources.SourceScan scan = sources.scan(projectId);

        assertThat(scan.files())
                .extracting(
                        ProjectSources.SourceFile::relativePath,
                        ProjectSources.SourceFile::sizeBytes,
                        ProjectSources.SourceFile::language)
                .containsExactly(
                        tuple(".env.example", 7L, "text"),
                        tuple("README.md", 6L, "markdown"),
                        tuple("src/Main.java", 13L, "java"),
                        tuple("src/util/Helper.kt", 13L, "kotlin"));
        assertThat(scan.files()).hasSize((int) scan.result().totals().includedFiles());
        assertThat(scan.files().stream().mapToLong(ProjectSources.SourceFile::sizeBytes).sum())
                .isEqualTo(scan.result().totals().includedBytes());
        Path repo = TestFileSystemInitializer.ROOTS_DIR.resolve("work/inventory-repo");
        assertThat(scan.files().getFirst().path().toRealPath())
                .isEqualTo(repo.resolve(".env.example").toRealPath());
        assertThat(scan.settingsVersion()).isZero();
    }

    @Test
    void resolveReportsTheSameErrorsAsTheInventory() {
        assertCode(UUID.randomUUID(), ErrorCode.PROJECT_NOT_FOUND);
        assertCode(insertZipProject(jdbc, workspaceId), ErrorCode.SOURCE_NOT_IMPORTED);
        assertCode(localProject("work", "missing"), ErrorCode.SOURCE_DIRECTORY_NOT_FOUND);
        assertCode(localProject("default", "outside-link"), ErrorCode.SOURCE_IS_SYMLINK);
    }

    private void assertCode(UUID projectId, ErrorCode code) {
        assertThatThrownBy(() -> sources.resolve(projectId))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.code()).isEqualTo(code));
        assertThatThrownBy(() -> sources.scan(projectId))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }

    private UUID localProject(String rootKey, String relativePath) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO projects (id, workspace_id, name, source_type,
                                              source_root_key, source_relative_path,
                                              created_at, updated_at)
                        VALUES (:id, :workspaceId, 'P', 'LOCAL_FOLDER', :rootKey,
                                :relativePath, now(), now())
                        """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("rootKey", rootKey)
                .param("relativePath", relativePath)
                .update();
        return id;
    }
}
