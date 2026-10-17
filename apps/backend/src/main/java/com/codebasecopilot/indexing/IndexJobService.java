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
    private final IndexOutcomes outcomes;
    private final ProjectService projects;
    private final ProjectSources sources;
    private final IdGenerator idGenerator;
    private final LineChunker.Parameters chunking;
    private final Clock clock;

    IndexJobService(
            IndexJobRepository jobs,
            SnapshotRepository snapshots,
            IndexWorker worker,
            IndexOutcomes outcomes,
            ProjectService projects,
            ProjectSources sources,
            IdGenerator idGenerator,
            LineChunker.Parameters chunking,
            Clock clock) {
        this.jobs = jobs;
        this.snapshots = snapshots;
        this.worker = worker;
        this.outcomes = outcomes;
        this.projects = projects;
        this.sources = sources;
        this.idGenerator = idGenerator;
        this.chunking = chunking;
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
                chunking.version(),
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
                false,
                now,
                null,
                null);
    }

    // A PENDING job ends right away; a RUNNING one is flagged and its worker stops at the next
    // heartbeat (D-084). The loop covers a job claimed or requeued between the two updates.
    IndexJob cancel(UUID id) {
        for (int i = 0; i < 3; i++) {
            if (outcomes.cancelPending(id)
                    || jobs.requestCancel(id, clock.instant().truncatedTo(ChronoUnit.MICROS))) {
                return get(id);
            }
            IndexJobStatus status = get(id).status();
            if (status != IndexJobStatus.PENDING && status != IndexJobStatus.RUNNING) {
                break;
            }
        }
        throw new ApiException(ErrorCode.JOB_NOT_ACTIVE);
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
