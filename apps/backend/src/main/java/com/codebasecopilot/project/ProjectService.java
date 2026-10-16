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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final String REQUIRED = "validation.required";
    private static final String NOT_ALLOWED_FOR_ZIP = "validation.project.not-allowed-for-zip";

    private final ProjectRepository repository;
    private final ProjectJobRepository jobs;
    private final WorkspaceService workspaces;
    private final LocalRoots localRoots;
    private final ProjectImports imports;
    private final ProjectIndexes indexes;
    private final IdGenerator idGenerator;
    private final Clock clock;

    ProjectService(
            ProjectRepository repository,
            ProjectJobRepository jobs,
            WorkspaceService workspaces,
            LocalRoots localRoots,
            ProjectImports imports,
            ProjectIndexes indexes,
            IdGenerator idGenerator,
            Clock clock) {
        this.repository = repository;
        this.jobs = jobs;
        this.workspaces = workspaces;
        this.localRoots = localRoots;
        this.imports = imports;
        this.indexes = indexes;
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
                        null,
                        null,
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
    public ProjectSummary get(UUID id) {
        Project project = repository.findById(id).orElseThrow(ProjectService::notFound);
        return summarize(List.of(project)).getFirst();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectSummary> list(UUID workspaceId, PageRequestParams params) {
        workspaces.requireExists(workspaceId);
        return PageResponse.of(
                summarize(repository.findPageByWorkspace(workspaceId, params)),
                params,
                repository.countByWorkspace(workspaceId));
    }

    // The row lock serializes the busy check with concurrent job creation (D-021, D-041).
    @Transactional
    public void delete(UUID id) {
        repository.findByIdForUpdate(id).orElseThrow(ProjectService::notFound);
        if (jobs.existsActive(id)) {
            throw new ApiException(ErrorCode.PROJECT_BUSY);
        }
        imports.onProjectDeleted(id);
        repository.deleteById(id);
    }

    // Must run in the transaction that creates the job; the partial unique index makes the busy
    // check safe under concurrency (D-041).
    @Transactional
    public void startJob(UUID projectId, UUID jobId, JobKind kind) {
        try {
            jobs.insertActive(
                    jobId, projectId, kind, clock.instant().truncatedTo(ChronoUnit.MICROS));
        } catch (DataIntegrityViolationException ex) {
            if (ConstraintViolations.violates(ex, ProjectJobRepository.ACTIVE_UNIQUE)) {
                throw new ApiException(ErrorCode.PROJECT_BUSY);
            }
            if (ConstraintViolations.violates(ex, ProjectJobRepository.PROJECT_FK)) {
                throw notFound();
            }
            throw ex;
        }
    }

    // Must run in the transaction of the job's terminal transition.
    @Transactional
    public void finishJobs(Collection<UUID> jobIds) {
        jobs.deactivate(jobIds);
    }

    // Returns the previous current import, whose files the caller may now remove.
    @Transactional
    public @Nullable UUID replaceCurrentImport(UUID projectId, UUID importJobId) {
        Project project =
                repository.findByIdForUpdate(projectId).orElseThrow(ProjectService::notFound);
        repository.updateCurrentImport(
                projectId, importJobId, clock.instant().truncatedTo(ChronoUnit.MICROS));
        return project.currentImportJobId();
    }

    // Returns the previous active snapshot; only the index worker calls it, while the project
    // has an active job, so the value cannot change concurrently (D-041, D-044).
    @Transactional
    public @Nullable UUID activateSnapshot(UUID projectId, UUID snapshotId) {
        Project project =
                repository.findByIdForUpdate(projectId).orElseThrow(ProjectService::notFound);
        repository.updateActiveSnapshot(
                projectId, snapshotId, clock.instant().truncatedTo(ChronoUnit.MICROS));
        return project.activeSnapshotId();
    }

    // LOCAL_FOLDER projects of the same workspace and root whose path differs only by case;
    // on case-insensitive file systems they point to the same folder (D-020).
    @Transactional(readOnly = true)
    public List<Project> findCaseVariants(Project project) {
        if (project.sourceType() != SourceType.LOCAL_FOLDER) {
            return List.of();
        }
        return repository.findCaseVariants(project);
    }

    // Which of the given imports are still the current import of some project.
    @Transactional(readOnly = true)
    public Set<UUID> currentImportsAmong(Collection<UUID> importJobIds) {
        return repository.findCurrentImportIds(importJobIds);
    }

    // One batch lookup per page, never one per project.
    private List<ProjectSummary> summarize(List<Project> projects) {
        List<UUID> importIds =
                projects.stream()
                        .map(Project::currentImportJobId)
                        .filter(Objects::nonNull)
                        .toList();
        Map<UUID, ProjectImports.CurrentImport> current =
                importIds.isEmpty() ? Map.of() : imports.find(importIds);
        Map<UUID, ProjectIndexes.IndexState> index =
                projects.isEmpty() ? Map.of() : indexes.find(projects);
        return projects.stream()
                .map(
                        p ->
                                new ProjectSummary(
                                        p,
                                        p.currentImportJobId() == null
                                                ? null
                                                : current.get(p.currentImportJobId()),
                                        index.getOrDefault(p.id(), ProjectIndexes.IndexState.NONE)))
                .toList();
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
