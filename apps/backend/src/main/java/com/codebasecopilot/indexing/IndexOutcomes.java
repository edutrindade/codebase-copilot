package com.codebasecopilot.indexing;

import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.project.ProjectService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Terminal transitions: job, snapshot and job registry change in one transaction (D-041).
@Component
class IndexOutcomes {

    private final IndexJobRepository jobs;
    private final SnapshotRepository snapshots;
    private final IndexStore store;
    private final ChunkEmbeddingRepository embeddings;
    private final ProjectService projects;
    private final TransactionTemplate transactions;
    private final IndexingProperties properties;
    private final LineChunker.Parameters chunking;
    private final Clock clock;

    IndexOutcomes(
            IndexJobRepository jobs,
            SnapshotRepository snapshots,
            IndexStore store,
            ChunkEmbeddingRepository embeddings,
            ProjectService projects,
            TransactionTemplate transactions,
            IndexingProperties properties,
            LineChunker.Parameters chunking,
            Clock clock) {
        this.jobs = jobs;
        this.snapshots = snapshots;
        this.store = store;
        this.embeddings = embeddings;
        this.projects = projects;
        this.transactions = transactions;
        this.properties = properties;
        this.chunking = chunking;
        this.clock = clock;
    }

    // Returns false when the claim was lost and nothing changed. Embeddings already stored stay
    // cached, so the next job does not pay for them again (D-051).
    boolean fail(Claim claim, IndexErrorCode code) {
        return Boolean.TRUE.equals(
                transactions.execute(
                        tx -> {
                            Instant now = now();
                            if (!jobs.fail(claim, code, now)) {
                                return false;
                            }
                            discard(claim, now, false);
                            return true;
                        }));
    }

    // The active snapshot does not change; embeddings already stored stay cached (D-084).
    boolean cancel(Claim claim) {
        return Boolean.TRUE.equals(
                transactions.execute(
                        tx -> {
                            Instant now = now();
                            if (!jobs.cancel(claim, now)) {
                                return false;
                            }
                            discard(claim, now, true);
                            return true;
                        }));
    }

    boolean cancelPending(UUID jobId) {
        return Boolean.TRUE.equals(
                transactions.execute(
                        tx -> {
                            Instant now = now();
                            return jobs.cancelPending(jobId, now)
                                    .map(
                                            claim -> {
                                                discard(claim, now, true);
                                                return true;
                                            })
                                    .orElse(false);
                        }));
    }

    private void discard(Claim claim, Instant now, boolean cancelled) {
        if (cancelled) {
            snapshots.markCancelled(claim.snapshotId(), now);
        } else {
            snapshots.markFailed(claim.snapshotId(), now);
        }
        store.deleteFiles(claim.snapshotId());
        snapshots.removeUnreferencedContent(claim.projectId());
        projects.finishJobs(List.of(claim.jobId()));
    }

    // Activation, change summary, pruning and cleanup in one transaction, so a retried job
    // never sees a half-finished snapshot (D-044). Every chunk of the snapshot already has its
    // lexical row (D-056) and a vector of the embedding model, or embeddings are off (D-050).
    boolean complete(Claim claim, EmbeddingStage.@Nullable Result embedding) {
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
                                            chunking.version());
                            snapshots.recordLexical(
                                    claim.snapshotId(), IdentifierTokenizer.VERSION);
                            if (embedding != null) {
                                snapshots.recordEmbedding(
                                        claim.snapshotId(), embedding.model(), embedding.tokens());
                            }
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
                            embeddings.deleteOrphans();
                            projects.finishJobs(List.of(claim.jobId()));
                            return true;
                        }));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
