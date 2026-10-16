package com.codebasecopilot.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.error.FieldViolation;
import com.codebasecopilot.shared.localroots.LocalRoots;
import com.codebasecopilot.shared.localroots.LocalRootsProperties;
import com.codebasecopilot.workspace.WorkspaceService;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;

class ProjectServiceTest {

    private static final UUID ID = UUID.fromString("0192f5c4-7e3a-7b1e-9c3d-2f4a6b8c0d1e");
    private static final UUID WORKSPACE = UUID.fromString("0192f5c4-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00.123456789Z");

    @TempDir static Path rootDir;

    private final ProjectRepository repository = mock(ProjectRepository.class);
    private final WorkspaceService workspaces = mock(WorkspaceService.class);
    private final ProjectService service =
            service(LocalRoots.from(new LocalRootsProperties(rootDir.toString(), null, List.of())));

    @Test
    void createsZipProject() {
        Project project = service.create(WORKSPACE, zip("  Meu ZIP "));

        Instant micros = Instant.parse("2026-10-15T12:00:00.123456Z");
        assertThat(project)
                .isEqualTo(
                        new Project(
                                ID,
                                WORKSPACE,
                                "Meu ZIP",
                                SourceType.ZIP,
                                null,
                                null,
                                micros,
                                micros));
        verify(repository).insert(project);
    }

    @Test
    void reportsAllValidationErrorsAtOnce() {
        ApiException ex =
                catchThrowableOfType(
                        ApiException.class,
                        () ->
                                service.create(
                                        WORKSPACE,
                                        new CreateProjectCommand(
                                                "  ", SourceType.ZIP, "default", "")));

        assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ex.violations())
                .containsExactlyInAnyOrder(
                        new FieldViolation("name", "validation.name.required"),
                        new FieldViolation("rootKey", "validation.project.not-allowed-for-zip"),
                        new FieldViolation(
                                "relativePath", "validation.project.not-allowed-for-zip"));
    }

    @Test
    void requiresSourceType() {
        ApiException ex =
                catchThrowableOfType(
                        ApiException.class,
                        () ->
                                service.create(
                                        WORKSPACE,
                                        new CreateProjectCommand("x", null, null, null)));

        assertThat(ex.violations())
                .containsExactly(new FieldViolation("sourceType", "validation.required"));
    }

    @Test
    void localFolderRequiresRootKeyAndRelativePath() {
        ApiException ex =
                catchThrowableOfType(
                        ApiException.class,
                        () ->
                                service.create(
                                        WORKSPACE,
                                        new CreateProjectCommand(
                                                "x", SourceType.LOCAL_FOLDER, " ", null)));

        assertThat(ex.violations())
                .containsExactlyInAnyOrder(
                        new FieldViolation("rootKey", "validation.required"),
                        new FieldViolation("relativePath", "validation.required"));
    }

    @Test
    void validationPrecedesWorkspaceLookup() {
        assertThatThrownBy(() -> service.create(WORKSPACE, zip("")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        verifyNoInteractions(workspaces);
    }

    @Test
    void missingWorkspaceIsNotFound() {
        doThrow(new ApiException(ErrorCode.WORKSPACE_NOT_FOUND))
                .when(workspaces)
                .requireExists(WORKSPACE);

        assertThatThrownBy(() -> service.create(WORKSPACE, zip("x")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND));
        verify(repository, never()).insert(any());
    }

    @Test
    void workspaceRemovedConcurrentlyIsNotFound() {
        doThrow(
                        new DataIntegrityViolationException(
                                "insert failed",
                                new SQLException(
                                        "violates foreign key constraint"
                                                + " \"projects_workspace_id_fkey\"")))
                .when(repository)
                .insert(any());

        assertThatThrownBy(() -> service.create(WORKSPACE, zip("x")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    @Test
    void otherIntegrityViolationsPropagate() {
        DataIntegrityViolationException other =
                new DataIntegrityViolationException(
                        "insert failed", new SQLException("constraint \"projects_name_length\""));
        doThrow(other).when(repository).insert(any());

        assertThatThrownBy(() -> service.create(WORKSPACE, zip("x"))).isSameAs(other);
    }

    @Test
    void getAndDeleteMissingProjectAreNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.empty());
        when(repository.deleteById(ID)).thenReturn(false);

        assertThatThrownBy(() -> service.get(ID))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        assertThatThrownBy(() -> service.delete(ID))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void createsLocalFolderProjectWithNormalizedPath() {
        Project project = service.create(WORKSPACE, local("default", "./repos//api/"));

        assertThat(project.sourceType()).isEqualTo(SourceType.LOCAL_FOLDER);
        assertThat(project.sourceRootKey()).isEqualTo("default");
        assertThat(project.sourceRelativePath()).isEqualTo("repos/api");
        verify(repository).insert(project);
    }

    @Test
    void forbiddenCharactersAreValidationErrorsEvenWithDotDot() {
        ApiException ex =
                catchThrowableOfType(
                        ApiException.class, () -> service.create(WORKSPACE, local("x", "..\\a")));

        assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ex.violations())
                .containsExactly(
                        new FieldViolation(
                                "relativePath", "validation.project.relative-path-chars"));
        verifyNoInteractions(workspaces);
    }

    @Test
    void missingWorkspaceWinsOverMissingRoots() {
        doThrow(new ApiException(ErrorCode.WORKSPACE_NOT_FOUND))
                .when(workspaces)
                .requireExists(WORKSPACE);
        ProjectService withoutRoots =
                service(LocalRoots.from(new LocalRootsProperties(null, null, List.of())));

        assertCode(
                () -> withoutRoots.create(WORKSPACE, local("default", "a")),
                ErrorCode.WORKSPACE_NOT_FOUND);
    }

    @Test
    void localFolderWithoutConfiguredRootsIsConflict() {
        ProjectService withoutRoots =
                service(LocalRoots.from(new LocalRootsProperties(null, null, List.of())));

        assertCode(
                () -> withoutRoots.create(WORKSPACE, local("unknown", "../x")),
                ErrorCode.LOCAL_ROOTS_NOT_CONFIGURED);
        assertThat(withoutRoots.create(WORKSPACE, zip("ZIP ainda funciona")).sourceType())
                .isEqualTo(SourceType.ZIP);
    }

    @Test
    void unknownRootWinsOverPathOutsideRoot() {
        assertCode(
                () -> service.create(WORKSPACE, local("unknown", "../x")),
                ErrorCode.ROOT_NOT_FOUND);
    }

    @Test
    void pathOutsideRootIsRejectedBeforeInsert() {
        assertCode(
                () -> service.create(WORKSPACE, local("default", "a/../../x")),
                ErrorCode.PATH_OUTSIDE_ROOT);
        verify(repository, never()).insert(any());
    }

    @Test
    void duplicateLocalFolderIsConflict() {
        doThrow(
                        new DataIntegrityViolationException(
                                "insert failed",
                                new SQLException(
                                        "duplicate key value violates unique constraint"
                                                + " \"projects_local_folder_unique_idx\"")))
                .when(repository)
                .insert(any());

        assertCode(
                () -> service.create(WORKSPACE, local("default", "a")),
                ErrorCode.PROJECT_ALREADY_EXISTS);
    }

    private ProjectService service(LocalRoots roots) {
        return new ProjectService(
                repository, workspaces, roots, () -> ID, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }

    private static CreateProjectCommand local(String rootKey, String relativePath) {
        return new CreateProjectCommand("Local", SourceType.LOCAL_FOLDER, rootKey, relativePath);
    }

    private static CreateProjectCommand zip(String name) {
        return new CreateProjectCommand(name, SourceType.ZIP, null, null);
    }
}
