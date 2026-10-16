package com.codebasecopilot.indexing;

import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.project.ProjectService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Terminal transitions: job, snapshot and job registry change in one transaction (D-041).
@Component
class IndexOutcomes {

    private final IndexJobRepository jobs;
    private final SnapshotRepository snapshots;
    private final IndexStore store;
    private final ProjectService projects;
    private final TransactionTemplate transactions;
    private final IndexingProperties properties;
    private final Clock clock;

    IndexOutcomes(
            IndexJobRepository jobs,
            SnapshotRepository snapshots,
            IndexStore store,
            ProjectService projects,
            TransactionTemplate transactions,
            IndexingProperties properties,
            Clock clock) {
        this.jobs = jobs;
        this.snapshots = snapshots;
        this.store = store;
        this.projects = projects;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    // Returns false when the claim was lost and nothing changed.
    boolean fail(Claim claim, IndexErrorCode code) {
        return Boolean.TRUE.equals(
                transactions.execute(
                        tx -> {
                            Instant now = now();
                            if (!jobs.fail(claim, code, now)) {
                                return false;
                            }
                            snapshots.markFailed(claim.snapshotId(), now);
                            store.deleteFiles(claim.snapshotId());
                            snapshots.removeUnreferencedContent(claim.projectId());
                            projects.finishJobs(List.of(claim.jobId()));
                            return true;
                        }));
    }

    // Activation, change summary, pruning and cleanup in one transaction, so a retried job
    // never sees a half-finished snapshot (D-044).
    boolean complete(Claim claim) {
        return Boolean.TRUE.equals(
                transactions.execute(
                        tx -> {
                            Instant now = now();
                            if (!jobs.complete(claim, now)) {
                                return false;
                            }
                            SnapshotRepository.Counts counts =
                                    snapshots.count(
                                            claim.snapshotId(),
                                            claim.projectId(),
                                            LineChunker.VERSION);
                            UUID previous =
                                    projects.activateSnapshot(
                                            claim.projectId(), claim.snapshotId());
                            snapshots.markFinished(
                                    claim.snapshotId(),
                                    counts.failedFiles() > 0
                                            ? SnapshotStatus.PARTIAL
                                            : SnapshotStatus.READY,
                                    counts,
                                    previous == null
                                            ? null
                                            : snapshots.changes(previous, claim.snapshotId()),
                                    now);
                            snapshots.prune(
                                    claim.projectId(),
                                    claim.snapshotId(),
                                    properties.retainedSnapshots(),
                                    now);
                            snapshots.removeUnreferencedContent(claim.projectId());
                            projects.finishJobs(List.of(claim.jobId()));
                            return true;
                        }));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
