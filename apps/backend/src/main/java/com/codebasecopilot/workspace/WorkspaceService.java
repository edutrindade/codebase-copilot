package com.codebasecopilot.workspace;

import com.codebasecopilot.shared.IdGenerator;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import com.codebasecopilot.shared.persistence.ConstraintViolations;
import com.codebasecopilot.shared.text.DisplayNames;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    static final String DEFAULT_NAME = "Meus projetos";

    private final WorkspaceRepository repository;
    private final WorkspaceProjectCounter projectCounter;
    private final IdGenerator idGenerator;
    private final Clock clock;

    WorkspaceService(
            WorkspaceRepository repository,
            WorkspaceProjectCounter projectCounter,
            IdGenerator idGenerator,
            Clock clock) {
        this.repository = repository;
        this.projectCounter = projectCounter;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public Workspace create(@Nullable String rawName) {
        String name = DisplayNames.normalize(rawName, "name");
        // PostgreSQL stores microseconds; truncating keeps the response equal to the stored row.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Workspace workspace =
                new Workspace(idGenerator.newId(), name.isEmpty() ? DEFAULT_NAME : name, now, now);
        repository.insert(workspace);
        return workspace;
    }

    @Transactional(readOnly = true)
    public WorkspaceSummary get(UUID id) {
        Workspace workspace = repository.findById(id).orElseThrow(WorkspaceService::notFound);
        return new WorkspaceSummary(workspace, projectCounter.countByWorkspace(id));
    }

    @Transactional(readOnly = true)
    public void requireExists(UUID id) {
        if (!repository.existsById(id)) {
            throw notFound();
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkspaceSummary> list(PageRequestParams params) {
        List<Workspace> page = repository.findPage(params);
        Map<UUID, Long> counts =
                projectCounter.countByWorkspaces(page.stream().map(Workspace::id).toList());
        List<WorkspaceSummary> summaries =
                page.stream()
                        .map(w -> new WorkspaceSummary(w, counts.getOrDefault(w.id(), 0L)))
                        .toList();
        return PageResponse.of(summaries, params, repository.count());
    }

    @Transactional
    public void delete(UUID id) {
        if (projectCounter.countByWorkspace(id) > 0) {
            throw notEmpty();
        }
        boolean deleted;
        try {
            deleted = repository.deleteById(id);
        } catch (DataIntegrityViolationException ex) {
            // A project was created after the count; the RESTRICT foreign key caught it.
            if (ConstraintViolations.violates(ex, WorkspaceRepository.PROJECTS_FK)) {
                throw notEmpty();
            }
            throw ex;
        }
        if (!deleted) {
            throw notFound();
        }
    }

    private static ApiException notEmpty() {
        return new ApiException(ErrorCode.WORKSPACE_NOT_EMPTY);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.WORKSPACE_NOT_FOUND);
    }
}
