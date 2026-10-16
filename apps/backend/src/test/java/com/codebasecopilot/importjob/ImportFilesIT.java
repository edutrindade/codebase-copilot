package com.codebasecopilot.importjob;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.shared.datadir.DataDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ImportFilesIT {

    @Autowired private ImportFiles files;
    @Autowired private DataDirectory dataDirectory;
    @Autowired private TransactionTemplate transactions;

    @Test
    void deletesFoldersOnlyAfterCommit() throws IOException {
        UUID id = UUID.randomUUID();
        Path dir = folderWithFile(id);

        transactions.executeWithoutResult(
                status -> {
                    files.deleteAfterCommit(List.of(id));
                    assertThat(dir).exists();
                });

        assertThat(dir).doesNotExist();
    }

    @Test
    void rollbackKeepsFolders() throws IOException {
        UUID id = UUID.randomUUID();
        Path dir = folderWithFile(id);

        transactions.executeWithoutResult(
                status -> {
                    files.deleteAfterCommit(List.of(id));
                    status.setRollbackOnly();
                });

        assertThat(dir).isDirectory();
    }

    @Test
    void deletingNeverFollowsLinks() throws IOException {
        Path outside = Files.createTempDirectory("codebase-copilot-outside-");
        Path target = Files.writeString(outside.resolve("keep.txt"), "keep");
        UUID id = UUID.randomUUID();
        Path dir = Files.createDirectories(dataDirectory.importDir(id));
        Files.createSymbolicLink(dir.resolve("link"), outside);

        files.delete(id);

        assertThat(dir).doesNotExist();
        assertThat(target).hasContent("keep");
        Files.delete(target);
        Files.delete(outside);
    }

    private Path folderWithFile(UUID id) throws IOException {
        Path dir = Files.createDirectories(dataDirectory.importDir(id).resolve("source/nested"));
        Files.writeString(dir.resolve("file.txt"), "text");
        return dataDirectory.importDir(id);
    }
}
