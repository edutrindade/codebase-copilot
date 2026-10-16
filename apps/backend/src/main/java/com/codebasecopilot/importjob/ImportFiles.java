package com.codebasecopilot.importjob;

import com.codebasecopilot.shared.datadir.DataDirectory;
import com.codebasecopilot.shared.fs.Directories;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

// Folders of import jobs in DATA_DIR/imports/<importJobId> (D-028).
@Component
class ImportFiles {

    private static final Logger log = LoggerFactory.getLogger(ImportFiles.class);

    private final DataDirectory dataDirectory;

    ImportFiles(DataDirectory dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    // A rollback keeps the folders, because the rows that own them still exist.
    void deleteAfterCommit(Collection<UUID> importJobIds) {
        List<UUID> ids = List.copyOf(importJobIds);
        if (ids.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        ids.forEach(ImportFiles.this::delete);
                    }
                });
    }

    Path uploadFile(UUID importJobId) {
        return dataDirectory.importDir(importJobId).resolve("upload.zip");
    }

    Path sourceDir(UUID importJobId) {
        return dataDirectory.importDir(importJobId).resolve("source");
    }

    // Called inside the transaction that inserts the job; a rollback removes the folder.
    void storeUpload(UUID importJobId, MultipartFile file) throws IOException {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            delete(importJobId);
                        }
                    }
                });
        Files.createDirectory(dataDirectory.importDir(importJobId));
        file.transferTo(uploadFile(importJobId));
    }

    void delete(UUID importJobId) {
        try {
            Directories.deleteRecursively(dataDirectory.importDir(importJobId));
        } catch (IOException e) {
            // The exception message may contain names taken from the imported content.
            log.warn(
                    "Could not delete files of import {}: {}",
                    importJobId,
                    e.getClass().getSimpleName());
        }
    }
}
