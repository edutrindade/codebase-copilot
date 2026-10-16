package com.codebasecopilot.indexing;

import com.codebasecopilot.importjob.scan.ProjectSources;
import com.codebasecopilot.importjob.scan.ScanResult;
import com.codebasecopilot.shared.error.ApiException;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// The pipeline of one claimed job (D-044). Logs carry ids, counts and codes only, never paths.
@Component
class IndexRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexRunner.class);

    private static final Set<IndexErrorCode> SOURCE_ERRORS =
            EnumSet.of(
                    IndexErrorCode.SOURCE_ROOT_NOT_CONFIGURED,
                    IndexErrorCode.SOURCE_DIRECTORY_NOT_FOUND,
                    IndexErrorCode.SOURCE_IS_SYMLINK,
                    IndexErrorCode.SOURCE_NOT_READABLE,
                    IndexErrorCode.SOURCE_NOT_IMPORTED);

    private final IndexJobRepository jobs;
    private final SnapshotRepository snapshots;
    private final IndexStore store;
    private final IndexOutcomes outcomes;
    private final ProjectSources sources;
    private final JsonMapper json;
    private final IndexingProperties properties;
    private final Clock clock;

    IndexRunner(
            IndexJobRepository jobs,
            SnapshotRepository snapshots,
            IndexStore store,
            IndexOutcomes outcomes,
            ProjectSources sources,
            JsonMapper json,
            IndexingProperties properties,
            Clock clock) {
        this.jobs = jobs;
        this.snapshots = snapshots;
        this.store = store;
        this.outcomes = outcomes;
        this.sources = sources;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
    }

    void run(Claim claim) {
        Heartbeat heartbeat = new Heartbeat(jobs, claim, clock, properties.heartbeatInterval());
        try {
            heartbeat.stage(IndexStage.SCANNING, null, null);
            if (claim.attempt() > 1) {
                store.deleteFiles(claim.snapshotId());
            }
            ProjectSources.SourceScan scan = scan(claim, heartbeat);
            check(scan);
            heartbeat.stage(IndexStage.FINALIZING, scan.files().size(), 0);
            if (outcomes.complete(claim, new SnapshotRepository.Counts(0, 0, 0, 0, 0, 0))) {
                log.info("Index job {} completed", claim.jobId());
            }
        } catch (IndexFailure e) {
            if (outcomes.fail(claim, e.code())) {
                log.info("Index job {} failed: {}", claim.jobId(), e.code());
            }
        } catch (LostClaimException e) {
            log.info("Index job {} attempt {} lost its claim", claim.jobId(), claim.attempt());
        } catch (RuntimeException e) {
            log.warn(
                    "Index job {} attempt {} failed: {}",
                    claim.jobId(),
                    claim.attempt(),
                    e.getClass().getSimpleName());
            if (claim.attempt() < properties.maxAttempts()) {
                jobs.requeue(claim);
            } else {
                outcomes.fail(claim, IndexErrorCode.INDEX_IO_ERROR);
            }
        }
    }

    private ProjectSources.SourceScan scan(Claim claim, Heartbeat heartbeat) {
        ProjectSources.SourceScan scan;
        try {
            scan = sources.scan(claim.projectId(), heartbeat::tick);
        } catch (ApiException e) {
            throw new IndexFailure(
                    SOURCE_ERRORS.stream()
                            .filter(code -> code.name().equals(e.code().name()))
                            .findFirst()
                            .orElseThrow(() -> e));
        }
        snapshots.recordScan(
                claim.snapshotId(), scan.settingsVersion(), json.writeValueAsString(scan.result()));
        return scan;
    }

    static void check(ProjectSources.SourceScan scan) {
        ScanResult result = scan.result();
        if (result.truncated()) {
            throw new IndexFailure(IndexErrorCode.SCAN_LIMIT_REACHED);
        }
        if (result.totals().includedBytes() > scan.maxProjectBytes()) {
            throw new IndexFailure(IndexErrorCode.PROJECT_SIZE_LIMIT_EXCEEDED);
        }
        if (scan.files().isEmpty()) {
            throw new IndexFailure(IndexErrorCode.NO_INCLUDED_FILES);
        }
    }
}
