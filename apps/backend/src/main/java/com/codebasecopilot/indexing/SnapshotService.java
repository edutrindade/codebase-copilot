package com.codebasecopilot.indexing;

import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.error.FieldViolation;
import com.codebasecopilot.shared.fs.RelativePaths;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SnapshotService {

    private final SnapshotRepository snapshots;
    private final ProjectService projects;

    SnapshotService(SnapshotRepository snapshots, ProjectService projects) {
        this.snapshots = snapshots;
        this.projects = projects;
    }

    record SnapshotView(Snapshot snapshot, boolean active) {}

    @Transactional(readOnly = true)
    SnapshotView get(UUID id) {
        Snapshot snapshot = find(id);
        return new SnapshotView(snapshot, id.equals(activeSnapshotId(snapshot.projectId())));
    }

    @Transactional(readOnly = true)
    PageResponse<SnapshotView> list(UUID projectId, PageRequestParams params) {
        UUID active = activeSnapshotId(projectId);
        return PageResponse.of(
                        snapshots.findPageByProject(projectId, params),
                        params,
                        snapshots.countByProject(projectId))
                .map(s -> new SnapshotView(s, s.id().equals(active)));
    }

    // Filters are validated before the lookup, as in the other endpoints.
    @Transactional(readOnly = true)
    PageResponse<SnapshotRepository.FileItem> files(
            UUID id,
            @Nullable FileStatus status,
            @Nullable String pathPrefix,
            PageRequestParams params) {
        String prefix = prefix(pathPrefix);
        Snapshot snapshot = find(id);
        return PageResponse.of(
                snapshots.findFiles(snapshot, status, prefix, params),
                params,
                snapshots.countFiles(id, status, prefix));
    }

    private Snapshot find(UUID id) {
        return snapshots
                .findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.SNAPSHOT_NOT_FOUND));
    }

    private @Nullable UUID activeSnapshotId(UUID projectId) {
        return projects.get(projectId).project().activeSnapshotId();
    }

    private static String prefix(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        try {
            if (!RelativePaths.hasForbiddenCharacters(raw)) {
                return RelativePaths.normalize(raw);
            }
        } catch (ApiException e) {
            // A leading "/" or a ".." segment.
        }
        throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                List.of(new FieldViolation("pathPrefix", "validation.snapshot.path-prefix")));
    }
}
