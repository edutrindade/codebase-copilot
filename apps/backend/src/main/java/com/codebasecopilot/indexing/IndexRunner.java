package com.codebasecopilot.indexing;

import com.codebasecopilot.importjob.scan.ProjectSources;
import com.codebasecopilot.importjob.scan.ScanResult;
import com.codebasecopilot.indexing.content.ContentHash;
import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.indexing.content.TextClassifier;
import com.codebasecopilot.shared.error.ApiException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

// The pipeline of one claimed job (D-044). Logs carry ids, counts and codes only, never paths.
@Component
class IndexRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexRunner.class);

    // Bounds the memory of a batch when files are large.
    private static final long MAX_BATCH_BYTES = 16 * 1024 * 1024;

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
    private final SourceFileReader reader;
    private final TransactionTemplate transactions;
    private final ProjectSources sources;
    private final JsonMapper json;
    private final IndexingProperties properties;
    private final Clock clock;

    IndexRunner(
            IndexJobRepository jobs,
            SnapshotRepository snapshots,
            IndexStore store,
            IndexOutcomes outcomes,
            SourceFileReader reader,
            TransactionTemplate transactions,
            ProjectSources sources,
            JsonMapper json,
            IndexingProperties properties,
            Clock clock) {
        this.jobs = jobs;
        this.snapshots = snapshots;
        this.store = store;
        this.outcomes = outcomes;
        this.reader = reader;
        this.transactions = transactions;
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
            processFiles(claim, scan, heartbeat);
            heartbeat.stage(IndexStage.FINALIZING, scan.files().size(), scan.files().size());
            if (outcomes.complete(claim)) {
                log.info(
                        "Index job {} completed with {} files", claim.jobId(), scan.files().size());
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

    // Reads every included file, so files that failed in an earlier job are retried (D-045).
    // Contents already chunked with the current version are not decoded again.
    private void processFiles(Claim claim, ProjectSources.SourceScan scan, Heartbeat heartbeat) {
        List<ProjectSources.SourceFile> files = scan.files();
        heartbeat.stage(IndexStage.PROCESSING_FILES, files.size(), 0);
        List<Read> batch = new ArrayList<>();
        long batchBytes = 0;
        int completed = 0;
        for (ProjectSources.SourceFile file : files) {
            SourceFileReader.Result result = reader.read(file.path(), scan.maxFileBytes());
            batch.add(new Read(file, result));
            if (result instanceof SourceFileReader.Bytes bytes) {
                batchBytes += bytes.bytes().length;
            }
            if (batch.size() >= properties.batchFiles() || batchBytes >= MAX_BATCH_BYTES) {
                completed += batch.size();
                store(claim, batch, heartbeat, completed);
                batch.clear();
                batchBytes = 0;
            }
        }
        if (!batch.isEmpty()) {
            completed += batch.size();
            store(claim, batch, heartbeat, completed);
        }
    }

    private void store(Claim claim, List<Read> batch, Heartbeat heartbeat, int completed) {
        Map<ProjectSources.SourceFile, String> hashes = new HashMap<>();
        for (Read read : batch) {
            if (read.result() instanceof SourceFileReader.Bytes bytes) {
                hashes.put(read.file(), ContentHash.of(bytes.bytes()));
            }
        }
        Set<String> chunked =
                store.chunkedHashes(claim.projectId(), hashes.values(), LineChunker.VERSION);
        Map<String, TextClassifier.Classification> classified = new HashMap<>();
        Map<String, IndexStore.NewContent> contents = new LinkedHashMap<>();
        List<IndexStore.FileRow> rows = new ArrayList<>();
        for (Read read : batch) {
            ProjectSources.SourceFile file = read.file();
            if (read.result() instanceof SourceFileReader.Failed failed) {
                rows.add(row(file, FileStatus.FAILED, failed.reason().name(), null));
                continue;
            }
            byte[] bytes = ((SourceFileReader.Bytes) read.result()).bytes();
            String hash = hashes.get(file);
            if (chunked.contains(hash)) {
                rows.add(row(file, FileStatus.INDEXED, null, hash));
                continue;
            }
            switch (classified.computeIfAbsent(hash, h -> TextClassifier.classify(bytes))) {
                case TextClassifier.Text text -> {
                    contents.computeIfAbsent(
                            hash,
                            h ->
                                    new IndexStore.NewContent(
                                            h,
                                            text.content(),
                                            bytes.length,
                                            text.lines().size(),
                                            LineChunker.chunk(text.lines())));
                    rows.add(row(file, FileStatus.INDEXED, null, hash));
                }
                case TextClassifier.Unsupported unsupported ->
                        rows.add(
                                row(
                                        file,
                                        FileStatus.UNSUPPORTED,
                                        unsupported.reason().name(),
                                        null));
            }
        }
        transactions.executeWithoutResult(
                tx -> {
                    heartbeat.progress(completed);
                    store.insertContents(
                            claim.projectId(),
                            List.copyOf(contents.values()),
                            clock.instant().truncatedTo(ChronoUnit.MICROS));
                    store.insertFiles(claim.snapshotId(), claim.projectId(), rows);
                });
    }

    private static IndexStore.FileRow row(
            ProjectSources.SourceFile file,
            FileStatus status,
            @Nullable String reason,
            @Nullable String hash) {
        return new IndexStore.FileRow(
                file.relativePath(), status, reason, hash, file.language(), file.sizeBytes());
    }

    private record Read(ProjectSources.SourceFile file, SourceFileReader.Result result) {}

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
