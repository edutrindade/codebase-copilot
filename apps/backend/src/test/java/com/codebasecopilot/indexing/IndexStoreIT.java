package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.IndexRows.count;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.indexing.content.ContentHash;
import com.codebasecopilot.indexing.content.LineChunker;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class IndexStoreIT {

    @Autowired private IndexStore store;
    @Autowired private JdbcClient jdbc;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        projectId = insertZipProject(jdbc, insertWorkspace(jdbc));
    }

    @Test
    void insertsContentsAndChunksIdempotently() {
        List<IndexStore.NewContent> contents = List.of(content("a\nb"), content("c"));

        store.insertContents(projectId, contents, Instant.now());
        store.insertContents(projectId, contents, Instant.now());

        assertThat(count(jdbc, "file_contents")).isEqualTo(2);
        assertThat(count(jdbc, "chunks")).isEqualTo(2);
        assertThat(
                        jdbc.sql("SELECT content FROM chunks WHERE content_hash = :hash")
                                .param("hash", ContentHash.of("a\nb"))
                                .query(String.class)
                                .single())
                .isEqualTo("a\nb");
    }

    @Test
    void chunkedHashesNeedChunksOfTheVersionOrABlankContent() {
        IndexStore.NewContent text = content("x");
        IndexStore.NewContent blank = content(" \n\t\n");
        store.insertContents(projectId, List.of(text, blank), Instant.now());
        IndexStore.NewContent letterV = content("v");
        store.insertContents(
                projectId,
                List.of(new IndexStore.NewContent(letterV.contentHash(), "v", 1, 1, List.of())),
                Instant.now());

        List<String> hashes =
                List.of(text.contentHash(), blank.contentHash(), letterV.contentHash(), "missing");
        assertThat(store.chunkedHashes(projectId, hashes, LineChunker.VERSION))
                .containsExactlyInAnyOrder(text.contentHash(), blank.contentHash());
        assertThat(store.chunkedHashes(projectId, hashes, "lines-v2"))
                .containsExactly(blank.contentHash());
        assertThat(store.chunkedHashes(UUID.randomUUID(), hashes, LineChunker.VERSION)).isEmpty();
    }

    @Test
    void insertsAndDeletesSnapshotFiles() {
        IndexStore.NewContent text = content("x");
        store.insertContents(projectId, List.of(text), Instant.now());
        UUID snapshotId = insertSnapshot(jdbc, projectId, "BUILDING");
        List<IndexStore.FileRow> files =
                List.of(
                        new IndexStore.FileRow(
                                "a.txt", FileStatus.INDEXED, null, text.contentHash(), "text", 1),
                        new IndexStore.FileRow(
                                "b.bin", FileStatus.UNSUPPORTED, "BINARY", null, "text", 3),
                        new IndexStore.FileRow(
                                "c.txt", FileStatus.FAILED, "READ_ERROR", null, "text", 2));

        store.insertFiles(snapshotId, projectId, files);
        store.insertFiles(snapshotId, projectId, files);
        assertThat(count(jdbc, "snapshot_files")).isEqualTo(3);

        store.deleteFiles(snapshotId);
        assertThat(count(jdbc, "snapshot_files")).isZero();
    }

    private static IndexStore.NewContent content(String text) {
        List<String> lines = text.lines().toList();
        return new IndexStore.NewContent(
                ContentHash.of(text), text, text.length(), lines.size(), LineChunker.chunk(lines));
    }
}
