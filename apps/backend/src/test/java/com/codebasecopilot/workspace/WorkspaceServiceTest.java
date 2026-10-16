package com.codebasecopilot.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class WorkspaceServiceTest {

    private static final UUID ID = UUID.fromString("0192f5c4-7e3a-7b1e-9c3d-2f4a6b8c0d1e");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00.123456789Z");

    private final WorkspaceRepository repository = mock(WorkspaceRepository.class);
    private final WorkspaceProjectCounter counter = mock(WorkspaceProjectCounter.class);
    private final WorkspaceService service =
            new WorkspaceService(repository, counter, () -> ID, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsWithGeneratedIdAndMicrosecondTimestamps() {
        Workspace workspace = service.create("  Meus repos ");

        Instant micros = Instant.parse("2026-10-15T12:00:00.123456Z");
        assertThat(workspace).isEqualTo(new Workspace(ID, "Meus repos", micros, micros));
        verify(repository).insert(workspace);
    }

    @Test
    void usesDefaultNameWhenAbsentOrBlank() {
        assertThat(service.create(null).name()).isEqualTo("Meus projetos");
        assertThat(service.create("   ").name()).isEqualTo("Meus projetos");
    }

    @Test
    void rejectsInvalidNameBeforePersisting() {
        assertThatThrownBy(() -> service.create("a".repeat(101))).isInstanceOf(ApiException.class);
        verify(repository, never()).insert(any());
    }

    @Test
    void getMissingWorkspaceIsNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    @Test
    void deleteMissingWorkspaceIsNotFound() {
        when(repository.deleteById(ID)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(ID))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    @Test
    void requireExistsFailsForMissingWorkspace() {
        when(repository.existsById(ID)).thenReturn(false);

        assertThatThrownBy(() -> service.requireExists(ID))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    @Test
    void getIncludesProjectCount() {
        Workspace workspace = new Workspace(ID, "W", NOW, NOW);
        when(repository.findById(ID)).thenReturn(Optional.of(workspace));
        when(counter.countByWorkspace(ID)).thenReturn(3L);

        assertThat(service.get(ID)).isEqualTo(new WorkspaceSummary(workspace, 3));
    }

    @Test
    void listCountsInOneBatchAndDefaultsToZero() {
        UUID other = UUID.fromString("0192f5c4-0000-7000-8000-000000000002");
        Workspace first = new Workspace(ID, "A", NOW, NOW);
        Workspace second = new Workspace(other, "B", NOW, NOW);
        PageRequestParams params = new PageRequestParams(0, 20);
        when(repository.findPage(params)).thenReturn(List.of(first, second));
        when(repository.count()).thenReturn(2L);
        when(counter.countByWorkspaces(List.of(ID, other))).thenReturn(Map.of(ID, 2L));

        assertThat(service.list(params).items())
                .containsExactly(new WorkspaceSummary(first, 2), new WorkspaceSummary(second, 0));
        verify(counter, never()).countByWorkspace(any());
    }

    @Test
    void deleteWithProjectsIsConflict() {
        when(counter.countByWorkspace(ID)).thenReturn(1L);

        assertCode(() -> service.delete(ID), ErrorCode.WORKSPACE_NOT_EMPTY);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void foreignKeyViolationOnDeleteIsConflict() {
        when(repository.deleteById(ID))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "delete failed",
                                new SQLException(
                                        "update or delete on table \"workspaces\" violates foreign"
                                                + " key constraint \"projects_workspace_id_fkey\"")));

        assertCode(() -> service.delete(ID), ErrorCode.WORKSPACE_NOT_EMPTY);
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }
}
