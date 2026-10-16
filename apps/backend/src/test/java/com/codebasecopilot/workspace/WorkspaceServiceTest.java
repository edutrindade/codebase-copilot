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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceServiceTest {

    private static final UUID ID = UUID.fromString("0192f5c4-7e3a-7b1e-9c3d-2f4a6b8c0d1e");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00.123456789Z");

    private final WorkspaceRepository repository = mock(WorkspaceRepository.class);
    private final WorkspaceService service =
            new WorkspaceService(repository, () -> ID, Clock.fixed(NOW, ZoneOffset.UTC));

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
}
