package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.indexing.IndexingTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.DocumentContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
class SnapshotTreeApiIT {

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
                                "work/tree-" + UUID.randomUUID()));
        write("src/App.java", "class App {}\n");
        write("src/util/Strings.java", "class Strings {}\n");
        write("src/util/deep/Deep.java", "class Deep {}\n");
        write("srcx/Other.java", "class Other {}\n");
        write("ação/Ünï.java", "class U {}\n");
        write("README.md", "# readme\n");
        Files.createDirectories(tree.resolve("docs"));
        Files.write(tree.resolve("docs/latin1.txt"), "ação".getBytes(StandardCharsets.ISO_8859_1));
    }

    @AfterEach
    void removeTree() throws IOException {
        Directories.deleteRecursively(tree);
    }

    @Test
    void listsTheImmediateChildrenOfTheRoot() {
        String snapshotId = client.index(project());

        MvcTestResult result = tree(snapshotId, null, null);

        assertThat(result).hasStatusOk();
        DocumentContext body = json(result);
        assertThat((String) body.read("$.path")).isEmpty();
        assertThat((List<String>) body.read("$.entries[*].name"))
                .containsExactly("ação", "docs", "src", "srcx", "README.md");
        assertThat((List<String>) body.read("$.entries[*].type"))
                .containsExactly("DIRECTORY", "DIRECTORY", "DIRECTORY", "DIRECTORY", "FILE");
        assertThat((Integer) body.read("$.entries[2].files")).isEqualTo(3);
        assertThat((Integer) body.read("$.entries[2].indexedFiles")).isEqualTo(3);
        assertThat((Object) body.read("$.entries[2].status")).isNull();
        assertThat((Integer) body.read("$.entries[1].unsupportedFiles")).isEqualTo(1);
        assertThat((String) body.read("$.entries[4].path")).isEqualTo("README.md");
        assertThat((String) body.read("$.entries[4].status")).isEqualTo("INDEXED");
        assertThat((String) body.read("$.entries[4].language")).isEqualTo("markdown");
        assertThat(((Number) body.read("$.entries[4].sizeBytes")).longValue()).isEqualTo(9);
    }

    @Test
    void listsASubdirectoryWithoutSiblingsThatShareThePrefix() {
        String snapshotId = client.index(project());

        DocumentContext src = json(tree(snapshotId, "./src/", null));
        assertThat((String) src.read("$.path")).isEqualTo("src");
        assertThat((List<String>) src.read("$.entries[*].path"))
                .containsExactly("src/util", "src/App.java");
        assertThat((Integer) src.read("$.entries[0].files")).isEqualTo(2);

        DocumentContext accented = json(tree(snapshotId, "ação", null));
        assertThat((List<String>) accented.read("$.entries[*].name")).containsExactly("Ünï.java");
    }

    @Test
    void filtersByStatus() {
        String snapshotId = client.index(project());

        DocumentContext root = json(tree(snapshotId, null, "UNSUPPORTED"));
        assertThat((List<String>) root.read("$.entries[*].name")).containsExactly("docs");

        DocumentContext docs = json(tree(snapshotId, "docs", "UNSUPPORTED"));
        assertThat((String) docs.read("$.entries[0].reason")).isEqualTo("INVALID_ENCODING");
    }

    @Test
    void unknownDirectoriesAndPrunedSnapshotsAreEmpty() {
        String snapshotId = client.index(project());

        assertThat((List<?>) json(tree(snapshotId, "missing", null)).read("$.entries")).isEmpty();
        assertThat((List<?>) json(tree(snapshotId, "README.md", null)).read("$.entries")).isEmpty();
    }

    @Test
    void invalidInputIsRejectedBeforeTheLookup() {
        String unknown = UUID.randomUUID().toString();

        for (String path : new String[] {"/etc", "../x", "a\\b", "a\u0000b"}) {
            MvcTestResult result = tree(unknown, path, null);
            assertError(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
            assertThat((String) json(result).read("$.errors[0].field")).isEqualTo("path");
        }
        assertError(tree(unknown, null, "DELETED"), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertError(tree(unknown, null, null), HttpStatus.NOT_FOUND, "SNAPSHOT_NOT_FOUND");
    }

    private MvcTestResult tree(String snapshotId, String path, String status) {
        var request = mvc.get().uri("/api/v1/snapshots/{id}/tree", snapshotId);
        if (path != null) {
            request = request.param("path", path);
        }
        if (status != null) {
            request = request.param("status", status);
        }
        return request.exchange();
    }

    private void write(String path, String content) throws IOException {
        Path file = tree.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void assertError(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat((String) json(result).read("$.code")).isEqualTo(code);
    }

    private String project() {
        return client.localProject(workspaceId, "work", tree.getFileName().toString());
    }
}
