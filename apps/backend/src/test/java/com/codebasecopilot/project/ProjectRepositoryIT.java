package com.codebasecopilot.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class ProjectRepositoryIT {

    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00.123456Z");

    @Autowired private ProjectRepository repository;
    @Autowired private JdbcClient jdbc;

    private UUID workspace;
    private UUID otherWorkspace;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        workspace = insertWorkspace();
        otherWorkspace = insertWorkspace();
    }

    @Test
    void insertsAndFindsBothSourceTypes() {
        Project zip = zip(workspace, "0192f5c4-0000-7000-8000-000000000001", T0);
        Project local =
                new Project(
                        UUID.fromString("0192f5c4-0000-7000-8000-000000000002"),
                        workspace,
                        "Local",
                        SourceType.LOCAL_FOLDER,
                        "default",
                        "",
                        T0,
                        T0);

        repository.insert(zip);
        repository.insert(local);

        assertThat(repository.findById(zip.id())).contains(zip);
        assertThat(repository.findById(local.id())).contains(local);
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listsOnlyTheWorkspaceNewestFirst() {
        Project oldest = zip(workspace, "0192f5c4-0000-7000-8000-000000000009", T0);
        Project tieLow = zip(workspace, "0192f5c4-0000-7000-8000-000000000001", T0.plusSeconds(1));
        Project tieHigh = zip(workspace, "0192f5c4-0000-7000-8000-000000000002", T0.plusSeconds(1));
        Project elsewhere = zip(otherWorkspace, "0192f5c4-0000-7000-8000-000000000003", T0);
        for (Project p : new Project[] {tieLow, oldest, elsewhere, tieHigh}) {
            repository.insert(p);
        }

        assertThat(repository.findPageByWorkspace(workspace, new PageRequestParams(0, 20)))
                .containsExactly(tieHigh, tieLow, oldest);
        assertThat(repository.findPageByWorkspace(workspace, new PageRequestParams(1, 2)))
                .containsExactly(oldest);
        assertThat(repository.countByWorkspace(workspace)).isEqualTo(3);
        assertThat(repository.countByWorkspace(otherWorkspace)).isEqualTo(1);
    }

    @Test
    void countsProjectsOfSeveralWorkspacesInOneQuery() {
        UUID empty = insertWorkspace();
        repository.insert(zip(workspace, "0192f5c4-0000-7000-8000-000000000001", T0));
        repository.insert(zip(workspace, "0192f5c4-0000-7000-8000-000000000002", T0));
        repository.insert(zip(otherWorkspace, "0192f5c4-0000-7000-8000-000000000003", T0));

        assertThat(repository.countByWorkspaces(List.of(workspace, otherWorkspace, empty)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(workspace, 2L, otherWorkspace, 1L));
        assertThat(repository.countByWorkspaces(List.of())).isEmpty();
    }

    @Test
    void deleteReportsWhetherRowExisted() {
        Project project = zip(workspace, "0192f5c4-0000-7000-8000-000000000001", T0);
        repository.insert(project);

        assertThat(repository.deleteById(project.id())).isTrue();
        assertThat(repository.deleteById(project.id())).isFalse();
    }

    @Test
    void foreignKeyRejectsUnknownWorkspace() {
        assertViolates(
                ProjectRepository.WORKSPACE_FK, UUID.randomUUID(), "ZIP", null, null, "Projeto");
    }

    @Test
    void foreignKeyBlocksWorkspaceRemoval() {
        repository.insert(zip(workspace, "0192f5c4-0000-7000-8000-000000000001", T0));

        assertThatThrownBy(
                        () ->
                                jdbc.sql("DELETE FROM workspaces WHERE id = :id")
                                        .param("id", workspace)
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(ProjectRepository.WORKSPACE_FK);
    }

    @Test
    void checksRejectInconsistentSources() {
        assertViolates("projects_source_consistency", workspace, "ZIP", "default", null, "P");
        assertViolates("projects_source_consistency", workspace, "ZIP", null, "", "P");
        assertViolates(
                "projects_source_consistency", workspace, "LOCAL_FOLDER", "default", null, "P");
        assertViolates("projects_source_consistency", workspace, "LOCAL_FOLDER", null, "", "P");
        // GIT also breaks projects_source_consistency; PostgreSQL may report either one.
        assertViolates("violates check constraint", workspace, "GIT", null, null, "P");
    }

    @Test
    void checkRejectsInvalidNameLength() {
        assertViolates("projects_name_length", workspace, "ZIP", null, null, "");
        assertViolates("projects_name_length", workspace, "ZIP", null, null, "a".repeat(101));
    }

    @Test
    void uniqueIndexRejectsDuplicateLocalFolderInSameWorkspace() {
        insertRaw(workspace, "LOCAL_FOLDER", "default", "repo", "A");
        insertRaw(otherWorkspace, "LOCAL_FOLDER", "default", "repo", "B");
        insertRaw(workspace, "LOCAL_FOLDER", "work", "repo", "C");
        insertRaw(workspace, "ZIP", null, null, "D");
        insertRaw(workspace, "ZIP", null, null, "D");

        assertViolates(
                "projects_local_folder_unique_idx",
                workspace,
                "LOCAL_FOLDER",
                "default",
                "repo",
                "E");
    }

    private void assertViolates(
            String constraint,
            UUID workspaceId,
            String sourceType,
            String rootKey,
            String relativePath,
            String name) {
        assertThatThrownBy(() -> insertRaw(workspaceId, sourceType, rootKey, relativePath, name))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    private void insertRaw(
            UUID workspaceId, String sourceType, String rootKey, String relativePath, String name) {
        jdbc.sql(
                        """
                        INSERT INTO projects (id, workspace_id, name, source_type, source_root_key,
                                              source_relative_path, created_at, updated_at)
                        VALUES (:id, :workspaceId, :name, :sourceType, :rootKey, :relativePath,
                                :now, :now)
                        """)
                .param("id", UUID.randomUUID())
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("sourceType", sourceType)
                .param("rootKey", rootKey)
                .param("relativePath", relativePath)
                .param("now", OffsetDateTime.now())
                .update();
    }

    private UUID insertWorkspace() {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO workspaces (id, name, created_at, updated_at)
                        VALUES (:id, 'W', now(), now())
                        """)
                .param("id", id)
                .update();
        return id;
    }

    private static Project zip(UUID workspaceId, String id, Instant createdAt) {
        return new Project(
                UUID.fromString(id),
                workspaceId,
                "Projeto " + id,
                SourceType.ZIP,
                null,
                null,
                createdAt,
                createdAt);
    }
}
