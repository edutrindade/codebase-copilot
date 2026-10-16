package com.codebasecopilot.indexing;

import com.codebasecopilot.project.ProjectService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private final Clock clock;

    IndexOutcomes(
            IndexJobRepository jobs,
            SnapshotRepository snapshots,
            IndexStore store,
            ProjectService projects,
            TransactionTemplate transactions,
            Clock clock) {
        this.jobs = jobs;
        this.snapshots = snapshots;
        this.store = store;
        this.projects = projects;
        this.transactions = transactions;
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
                            projects.finishJobs(List.of(claim.jobId()));
                            return true;
                        }));
    }

    // Provisional until the files stage exists: the snapshot is closed but not activated.
    boolean complete(Claim claim, SnapshotRepository.Counts counts) {
        return Boolean.TRUE.equals(
                transactions.execute(
                        tx -> {
                            Instant now = now();
                            if (!jobs.complete(claim, now)) {
                                tx.setRollbackOnly();
                                return false;
                            }
                            snapshots.markFinished(
                                    claim.snapshotId(), SnapshotStatus.READY, counts, null, now);
                            projects.finishJobs(List.of(claim.jobId()));
                            return true;
                        }));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
