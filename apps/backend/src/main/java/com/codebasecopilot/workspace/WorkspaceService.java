package com.codebasecopilot.workspace;

import com.codebasecopilot.shared.IdGenerator;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import com.codebasecopilot.shared.text.DisplayNames;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    static final String DEFAULT_NAME = "Meus projetos";

    private final WorkspaceRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    WorkspaceService(WorkspaceRepository repository, IdGenerator idGenerator, Clock clock) {
        this.repository = repository;
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
    public Workspace get(UUID id) {
        return repository.findById(id).orElseThrow(WorkspaceService::notFound);
    }

    @Transactional(readOnly = true)
    public void requireExists(UUID id) {
        if (!repository.existsById(id)) {
            throw notFound();
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<Workspace> list(PageRequestParams params) {
        return PageResponse.of(repository.findPage(params), params, repository.count());
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.deleteById(id)) {
            throw notFound();
        }
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.WORKSPACE_NOT_FOUND);
    }
}
