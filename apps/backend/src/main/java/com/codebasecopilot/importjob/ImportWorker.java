package com.codebasecopilot.importjob;

import com.codebasecopilot.project.ProjectService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Runs extractions in-process (D-034); jobs interrupted by a shutdown are failed on the next
// start by ImportRecovery. Logs carry ids, counts and codes only, never entry names.
@Component
class ImportWorker {

    private static final Logger log = LoggerFactory.getLogger(ImportWorker.class);

    private final ImportJobRepository repository;
    private final ImportFiles files;
    private final ZipExtractor extractor;
    private final ProjectService projects;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ExecutorService executor;

    ImportWorker(
            ImportJobRepository repository,
            ImportFiles files,
            ZipExtractor extractor,
            ProjectService projects,
            TransactionTemplate transactions,
            Clock clock,
            ImportProperties properties) {
        this.repository = repository;
        this.files = files;
        this.extractor = extractor;
        this.projects = projects;
        this.transactions = transactions;
        this.clock = clock;
        this.executor =
                Executors.newFixedThreadPool(
                        properties.maxConcurrent(),
                        Thread.ofPlatform().name("import-", 0).daemon().factory());
    }

    void submit(UUID importJobId) {
        executor.execute(() -> process(importJobId));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    void process(UUID id) {
        // The job may be gone (project removal is blocked, but tests truncate) or recovered.
        if (!repository.markExtracting(id)) {
            files.delete(id);
            return;
        }
        UUID projectId = repository.findById(id).map(ImportJob::projectId).orElse(null);
        if (projectId == null) {
            files.delete(id);
            return;
        }
        try {
            ZipExtractor.Result result =
                    extractor.extract(files.uploadFile(id), files.sourceDir(id));
            Files.deleteIfExists(files.uploadFile(id));
            complete(id, projectId, result);
        } catch (ZipRejectedException e) {
            finish(id, ImportStatus.REJECTED, e.code());
        } catch (ClosedByInterruptException e) {
            finish(id, ImportStatus.FAILED, ImportErrorCode.IMPORT_INTERRUPTED);
        } catch (IOException | RuntimeException e) {
            log.warn("Import {} failed: {}", id, e.getClass().getSimpleName());
            finish(id, ImportStatus.FAILED, ImportErrorCode.IMPORT_IO_ERROR);
        }
    }

    private void complete(UUID id, UUID projectId, ZipExtractor.Result result) {
        boolean completed =
                Boolean.TRUE.equals(
                        transactions.execute(
                                status -> {
                                    if (!repository.markCompleted(
                                            id,
                                            result.uncompressedBytes(),
                                            result.entryCount(),
                                            now())) {
                                        return false;
                                    }
                                    projects.finishJobs(List.of(id));
                                    UUID previous = projects.replaceCurrentImport(projectId, id);
                                    if (previous != null) {
                                        files.deleteAfterCommit(List.of(previous));
                                    }
                                    return true;
                                }));
        if (completed) {
            log.info("Import {} completed with {} entries", id, result.entryCount());
        } else {
            files.delete(id);
        }
    }

    private void finish(UUID id, ImportStatus status, ImportErrorCode code) {
        transactions.executeWithoutResult(
                tx -> {
                    if (repository.markFinished(id, status, code, now())) {
                        projects.finishJobs(List.of(id));
                    }
                });
        files.delete(id);
        log.info("Import {} {}: {}", id, status, code);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
