package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.indexing.IndexingTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.indexing.content.ContentHash;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.DocumentContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class SnapshotFileContentApiIT {

    private static final String SOURCE = "class App {\r\n  int x;\n}\n";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

    private IndexingTestClient client;
    private UUID workspaceId;
    private Path tree;

    @BeforeEach
    void setUp() throws IOException {
        DatabaseCleaner.truncateAll(jdbc);
        client = new IndexingTestClient(mvc);
        workspaceId = insertWorkspace(jdbc);
        tree =
                Files.createDirectories(
                        TestFileSystemInitializer.ROOTS_DIR.resolve(
                                "work/content-" + UUID.randomUUID()));
        Files.createDirectories(tree.resolve("src"));
        Files.writeString(tree.resolve("src/App.java"), SOURCE);
        Files.write(tree.resolve("latin1.txt"), "ação".getBytes(StandardCharsets.ISO_8859_1));
    }

    @AfterEach
    void removeTree() throws IOException {
        Directories.deleteRecursively(tree);
    }

    @Test
    void returnsTheContentAsIndexed() {
        String snapshotId = client.index(project());

        MvcTestResult result = content(snapshotId, "./src//App.java");

        assertThat(result).hasStatusOk();
        DocumentContext body = json(result);
        assertThat((String) body.read("$.snapshotId")).isEqualTo(snapshotId);
        assertThat((String) body.read("$.path")).isEqualTo("src/App.java");
        assertThat((String) body.read("$.language")).isEqualTo("java");
        assertThat((String) body.read("$.content")).isEqualTo(SOURCE);
        assertThat((String) body.read("$.contentHash"))
                .isEqualTo(ContentHash.of(SOURCE.getBytes(StandardCharsets.UTF_8)));
        assertThat(((Number) body.read("$.sizeBytes")).longValue())
                .isEqualTo(SOURCE.getBytes(StandardCharsets.UTF_8).length);
        DocumentContext listed = json(client.files(snapshotId, "pathPrefix=src"));
        assertThat((Integer) body.read("$.lineCount"))
                .isEqualTo(listed.read("$.items[0].lineCount"));
    }

    @Test
    void servesEachSnapshotItsOwnVersion() throws IOException {
        String projectId = project();
        String first = client.index(projectId);
        Files.writeString(tree.resolve("src/App.java"), "changed\n");
        Files.writeString(tree.resolve("src/New.java"), "new\n");
        String second = client.index(projectId);

        assertThat((String) json(content(first, "src/App.java")).read("$.content"))
                .isEqualTo(SOURCE);
        assertThat((String) json(content(second, "src/App.java")).read("$.content"))
                .isEqualTo("changed\n");
        assertError(content(first, "src/New.java"), HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
    }

    @Test
    void unindexedFilesHaveNoContent() {
        String snapshotId = client.index(project());

        assertError(content(snapshotId, "latin1.txt"), HttpStatus.CONFLICT, "FILE_NOT_INDEXED");
        assertError(content(snapshotId, "src"), HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
        assertError(content(snapshotId, "missing.txt"), HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
    }

    @Test
    void prunedSnapshotsAnswerConflict() {
        String snapshotId = client.index(project());
        jdbc.sql("UPDATE snapshots SET pruned_at = now() WHERE id = :id")
                .param("id", UUID.fromString(snapshotId))
                .update();

        assertError(content(snapshotId, "src/App.java"), HttpStatus.CONFLICT, "SNAPSHOT_PRUNED");
    }

    @Test
    void invalidPathsAreRejectedBeforeTheLookup() {
        String unknown = UUID.randomUUID().toString();

        for (String path : new String[] {"", ".", "/etc/passwd", "../x", "a\\b", "a\u0000b"}) {
            MvcTestResult result = content(unknown, path);
            assertError(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
            assertThat((String) json(result).read("$.errors[0].field")).isEqualTo("path");
        }
        assertError(
                mvc.get().uri("/api/v1/snapshots/{id}/files/content", unknown).exchange(),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
        assertError(content(unknown, "src/App.java"), HttpStatus.NOT_FOUND, "SNAPSHOT_NOT_FOUND");
    }

    private MvcTestResult content(String snapshotId, String path) {
        return mvc.get()
                .uri("/api/v1/snapshots/{id}/files/content", snapshotId)
                .param("path", path)
                .exchange();
    }

    private static void assertError(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat((String) json(result).read("$.code")).isEqualTo(code);
    }

    private String project() {
        return client.localProject(workspaceId, "work", tree.getFileName().toString());
    }
}
