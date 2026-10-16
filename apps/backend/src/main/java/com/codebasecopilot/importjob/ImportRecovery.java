package com.codebasecopilot.importjob;

import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.shared.datadir.DataDirectory;
import com.codebasecopilot.shared.fs.Directories;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Runs before the web server starts, so it never fails a job created by a new upload.
@Component
class ImportRecovery implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ImportRecovery.class);

    private final ImportJobRepository repository;
    private final ProjectService projects;
    private final DataDirectory dataDirectory;
    private final TransactionTemplate transactions;
    private final Clock clock;

    ImportRecovery(
            ImportJobRepository repository,
            ProjectService projects,
            DataDirectory dataDirectory,
            TransactionTemplate transactions,
            Clock clock) {
        this.repository = repository;
        this.projects = projects;
        this.dataDirectory = dataDirectory;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        recover();
    }

    void recover() {
        List<UUID> interrupted =
                Objects.requireNonNull(
                        transactions.execute(
                                tx -> {
                                    List<UUID> ids =
                                            repository.failAllActive(
                                                    ImportErrorCode.IMPORT_INTERRUPTED,
                                                    clock.instant().truncatedTo(ChronoUnit.MICROS));
                                    projects.finishJobs(ids);
                                    return ids;
                                }));
        if (!interrupted.isEmpty()) {
            log.warn("Marked {} interrupted imports as failed", interrupted.size());
        }
        Map<Path, @Nullable UUID> folders = folders();
        Set<UUID> current =
                projects.currentImportsAmong(
                        folders.values().stream().filter(Objects::nonNull).toList());
        int removed = 0;
        for (Map.Entry<Path, @Nullable UUID> folder : folders.entrySet()) {
            if (folder.getValue() == null || !current.contains(folder.getValue())) {
                try {
                    Directories.deleteRecursively(folder.getKey());
                    removed++;
                } catch (IOException e) {
                    log.warn(
                            "Could not delete an orphan import folder: {}",
                            e.getClass().getSimpleName());
                }
            }
        }
        if (removed > 0) {
            log.info("Removed {} orphan import folders", removed);
        }
    }

    // Every entry of DATA_DIR/imports; names that are not import ids map to null.
    private Map<Path, @Nullable UUID> folders() {
        Map<Path, @Nullable UUID> folders = new HashMap<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dataDirectory.importsDir())) {
            for (Path entry : entries) {
                folders.put(entry, parse(entry.getFileName().toString()));
            }
        } catch (IOException e) {
            log.warn("Could not list import folders: {}", e.getClass().getSimpleName());
        }
        return folders;
    }

    private static @Nullable UUID parse(String name) {
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
