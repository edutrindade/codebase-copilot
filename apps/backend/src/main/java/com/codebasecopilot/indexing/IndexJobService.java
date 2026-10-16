package com.codebasecopilot.indexing;

import com.codebasecopilot.importjob.scan.ProjectSources;
import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.project.JobKind;
import com.codebasecopilot.project.Project;
import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.shared.IdGenerator;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class IndexJobService {

    private final IndexJobRepository jobs;
    private final SnapshotRepository snapshots;
    private final IndexWorker worker;
    private final ProjectService projects;
    private final ProjectSources sources;
    private final IdGenerator idGenerator;
    private final Clock clock;

    IndexJobService(
            IndexJobRepository jobs,
            SnapshotRepository snapshots,
            IndexWorker worker,
            ProjectService projects,
            ProjectSources sources,
            IdGenerator idGenerator,
            Clock clock) {
        this.jobs = jobs;
        this.snapshots = snapshots;
        this.worker = worker;
        this.projects = projects;
        this.sources = sources;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    // Precedence: PROJECT_NOT_FOUND, SOURCE_*, PROJECT_BUSY (D-045). The source is checked
    // without scanning; the worker scans it again.
    @Transactional
    IndexJob create(UUID projectId) {
        sources.resolve(projectId);
        Project project = projects.get(projectId).project();
        UUID jobId = idGenerator.newId();
        UUID snapshotId = idGenerator.newId();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        projects.startJob(projectId, jobId, JobKind.INDEX);
        snapshots.insertBuilding(
                snapshotId,
                projectId,
                project.currentImportJobId(),
                sources.settingsVersion(projectId),
                LineChunker.VERSION,
                now);
        jobs.insertPending(jobId, projectId, snapshotId, now);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        worker.signal();
                    }
                });
        return new IndexJob(
                jobId,
                projectId,
                snapshotId,
                IndexJobStatus.PENDING,
                null,
                null,
                null,
                0,
                null,
                now,
                null,
                null);
    }

    @Transactional(readOnly = true)
    IndexJob get(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new ApiException(ErrorCode.INDEX_JOB_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    PageResponse<IndexJob> list(UUID projectId, PageRequestParams params) {
        projects.get(projectId);
        return PageResponse.of(
                jobs.findPageByProject(projectId, params), params, jobs.countByProject(projectId));
    }
}
