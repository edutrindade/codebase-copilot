package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.IndexRows.count;
import static com.codebasecopilot.indexing.IndexingTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.DocumentContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// A separate context: read failures need a spy on the file reader, and pruning a retention of
// one snapshot. Both would change the behavior of the shared context.
@IntegrationTest
@TestPropertySource(properties = "app.indexing.retained-snapshots=1")
class IndexingEdgeCasesIT {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @MockitoSpyBean private SourceFileReader reader;

    private IndexingTestClient client;
    private UUID workspaceId;
    private Path tree;

    @BeforeEach
    void setUp() throws IOException {
        DatabaseCleaner.truncateAll(jdbc);
        reset(reader);
        client = new IndexingTestClient(mvc);
        workspaceId = insertWorkspace(jdbc);
        tree =
                Files.createDirectories(
                        TestFileSystemInitializer.ROOTS_DIR.resolve(
                                "work/index-" + UUID.randomUUID()));
    }

    @AfterEach
    void removeTree() throws IOException {
        Directories.deleteRecursively(tree);
    }

    @Test
    void readFailuresMakeThePartialSnapshotActive() throws IOException {
        write("a.txt", "a");
        write("b.txt", "b");
        write("c.txt", "c");
        failReading("a.txt", ReadFailure.READ_ERROR);
        failReading("b.txt", ReadFailure.CHANGED_DURING_READ);

        String snapshotId = client.index(project());

        DocumentContext snapshot = client.snapshot(snapshotId);
        assertThat((String) snapshot.read("$.status")).isEqualTo("PARTIAL");
        assertThat((Boolean) snapshot.read("$.active")).isTrue();
        assertThat((Integer) snapshot.read("$.counts.failedFiles")).isEqualTo(2);
        DocumentContext failed = json(client.files(snapshotId, "status=FAILED"));
        assertThat((List<String>) failed.read("$.items[*].reason"))
                .containsExactly("READ_ERROR", "CHANGED_DURING_READ");
    }

    @Test
    void failedFilesAreReadAgainByTheNextJob() throws IOException {
        write("a.txt", "a");
        write("b.txt", "b");
        failReading("a.txt", ReadFailure.READ_ERROR);
        String projectId = project();
        client.index(projectId);
        reset(reader);

        String second = client.index(projectId);

        DocumentContext snapshot = client.snapshot(second);
        assertThat((String) snapshot.read("$.status")).isEqualTo("READY");
        assertThat((Map<String, Object>) snapshot.read("$.changes"))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("added", 1, "modified", 0, "removed", 0, "unchanged", 1));
    }

    @Test
    void pruningKeepsTheActiveAndTheRetainedSnapshots() throws IOException {
        write("shared.txt", "shared");
        write("one.txt", "1");
        String projectId = project();
        String first = client.index(projectId);
        Files.delete(tree.resolve("one.txt"));
        write("two.txt", "2");
        String second = client.index(projectId);
        Files.delete(tree.resolve("two.txt"));
        write("three.txt", "3");

        String third = client.index(projectId);

        assertThat((Object) client.snapshot(first).read("$.prunedAt")).isNotNull();
        assertThat((List<Object>) json(client.files(first, "")).read("$.items")).isEmpty();
        assertThat((Object) client.snapshot(second).read("$.prunedAt")).isNull();
        assertThat((Object) client.snapshot(third).read("$.prunedAt")).isNull();
        assertThat(
                        jdbc.sql("SELECT content FROM file_contents ORDER BY content")
                                .query(String.class)
                                .list())
                .containsExactly("2", "3", "shared");
        assertThat(count(jdbc, "chunks")).isEqualTo(3);
    }

    private void failReading(String name, ReadFailure reason) {
        doReturn(new SourceFileReader.Failed(reason))
                .when(reader)
                .read(argThat(path -> path != null && path.endsWith(name)), anyLong());
    }

    private String project() {
        return client.localProject(workspaceId, "work", tree.getFileName().toString());
    }

    private void write(String path, String content) throws IOException {
        Files.writeString(tree.resolve(path), content);
    }
}
