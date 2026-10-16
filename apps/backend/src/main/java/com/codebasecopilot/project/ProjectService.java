package com.codebasecopilot.project;

import com.codebasecopilot.shared.IdGenerator;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.error.FieldViolation;
import com.codebasecopilot.shared.fs.RelativePaths;
import com.codebasecopilot.shared.localroots.LocalRoots;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import com.codebasecopilot.shared.persistence.ConstraintViolations;
import com.codebasecopilot.shared.text.DisplayNames;
import com.codebasecopilot.workspace.WorkspaceService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final String REQUIRED = "validation.required";
    private static final String NOT_ALLOWED_FOR_ZIP = "validation.project.not-allowed-for-zip";

    private final ProjectRepository repository;
    private final WorkspaceService workspaces;
    private final LocalRoots localRoots;
    private final IdGenerator idGenerator;
    private final Clock clock;

    ProjectService(
            ProjectRepository repository,
            WorkspaceService workspaces,
            LocalRoots localRoots,
            IdGenerator idGenerator,
            Clock clock) {
        this.repository = repository;
        this.workspaces = workspaces;
        this.localRoots = localRoots;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    // Checks follow the precedence of D-032.
    @Transactional
    public Project create(UUID workspaceId, CreateProjectCommand command) {
        String name = validate(command);
        workspaces.requireExists(workspaceId);
        String rootKey = null;
        String relativePath = null;
        if (command.sourceType() == SourceType.LOCAL_FOLDER) {
            if (localRoots.isEmpty()) {
                throw new ApiException(ErrorCode.LOCAL_ROOTS_NOT_CONFIGURED);
            }
            rootKey = command.rootKey();
            if (localRoots.find(rootKey).isEmpty()) {
                throw new ApiException(ErrorCode.ROOT_NOT_FOUND);
            }
            relativePath = RelativePaths.normalize(command.relativePath());
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Project project =
                new Project(
                        idGenerator.newId(),
                        workspaceId,
                        name,
                        command.sourceType(),
                        rootKey,
                        relativePath,
                        now,
                        now);
        try {
            repository.insert(project);
        } catch (DataIntegrityViolationException ex) {
            // The workspace may have been removed after the existence check; duplicates are
            // detected by the unique index so concurrent creations are also covered.
            if (ConstraintViolations.violates(ex, ProjectRepository.WORKSPACE_FK)) {
                throw new ApiException(ErrorCode.WORKSPACE_NOT_FOUND);
            }
            if (ConstraintViolations.violates(ex, ProjectRepository.LOCAL_FOLDER_UNIQUE)) {
                throw new ApiException(ErrorCode.PROJECT_ALREADY_EXISTS);
            }
            throw ex;
        }
        return project;
    }

    @Transactional(readOnly = true)
    public Project get(UUID id) {
        return repository.findById(id).orElseThrow(ProjectService::notFound);
    }

    @Transactional(readOnly = true)
    public PageResponse<Project> list(UUID workspaceId, PageRequestParams params) {
        workspaces.requireExists(workspaceId);
        return PageResponse.of(
                repository.findPageByWorkspace(workspaceId, params),
                params,
                repository.countByWorkspace(workspaceId));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.deleteById(id)) {
            throw notFound();
        }
    }

    private static String validate(CreateProjectCommand command) {
        List<FieldViolation> violations = new ArrayList<>();
        String name = "";
        try {
            name = DisplayNames.normalize(command.name(), "name");
            if (name.isEmpty()) {
                violations.add(new FieldViolation("name", "validation.name.required"));
            }
        } catch (ApiException ex) {
            violations.addAll(ex.violations());
        }
        if (command.sourceType() == null) {
            violations.add(new FieldViolation("sourceType", REQUIRED));
        } else if (command.sourceType() == SourceType.ZIP) {
            if (command.rootKey() != null) {
                violations.add(new FieldViolation("rootKey", NOT_ALLOWED_FOR_ZIP));
            }
            if (command.relativePath() != null) {
                violations.add(new FieldViolation("relativePath", NOT_ALLOWED_FOR_ZIP));
            }
        } else {
            if (command.rootKey() == null || command.rootKey().isBlank()) {
                violations.add(new FieldViolation("rootKey", REQUIRED));
            }
            if (command.relativePath() == null) {
                violations.add(new FieldViolation("relativePath", REQUIRED));
            } else if (RelativePaths.hasForbiddenCharacters(command.relativePath())) {
                violations.add(
                        new FieldViolation(
                                "relativePath", "validation.project.relative-path-chars"));
            }
        }
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, violations);
        }
        return name;
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.PROJECT_NOT_FOUND);
    }
}
