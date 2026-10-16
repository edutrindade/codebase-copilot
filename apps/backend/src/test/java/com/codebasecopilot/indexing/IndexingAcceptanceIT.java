package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.IndexRows.count;
import static com.codebasecopilot.indexing.IndexingTestClient.body;
import static com.codebasecopilot.indexing.IndexingTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.indexing.content.ContentHash;
import com.codebasecopilot.shared.datadir.DataDirectory;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.DocumentContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Acceptance of Etapa 3: new, changed and removed files are reflected correctly (D-039, D-044).
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class IndexingAcceptanceIT {

    private static final String SECRET_NAME = "kept-out-of-logs-7f3a";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private DataDirectory dataDirectory;

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
                                "work/index-" + UUID.randomUUID()));
    }

    @AfterEach
    void removeTree() throws IOException {
        Directories.deleteRecursively(tree);
    }

    @Test
    void reflectsNewChangedRemovedAndUnchangedFiles(CapturedOutput output) throws IOException {
        write("keep.txt", "keep");
        write("change.txt", "v1");
        write("remove.txt", "bye");
        write("move-a.txt", "moved content");
        write("dup1.txt", "same");
        write("dup2.txt", "same");
        write("notes.log", "log");
        write("vendor/lib.txt", "lib");
        write("empty.txt", "");
        write(SECRET_NAME + ".txt", "secret name");
        String projectId = project();

        String first = client.index(projectId);

        DocumentContext snapshot1 = client.snapshot(first);
        assertThat((Map<String, Object>) snapshot1.read("$.counts"))
                .containsEntry("files", 9)
                .containsEntry("indexedFiles", 9)
                .containsEntry("chunks", 7);
        assertThat((Object) snapshot1.read("$.changes")).isNull();
        assertThat((Boolean) snapshot1.read("$.active")).isTrue();
        assertThat(count(jdbc, "file_contents")).isEqualTo(8);
        String keepChunk = chunkId("keep");

        write("change.txt", "v2");
        Files.delete(tree.resolve("remove.txt"));
        Files.createDirectories(tree.resolve("moved"));
        Files.move(tree.resolve("move-a.txt"), tree.resolve("moved/b.txt"));
        write("new.txt", "new");
        client.putSettings(
                projectId,
                "{\"excludePatterns\": [\"*.log\"], \"disabledDefaultRules\": [\"vendor\"]}");

        String second = client.index(projectId);

        DocumentContext snapshot2 = client.snapshot(second);
        assertThat((Map<String, Object>) snapshot2.read("$.changes"))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("added", 3, "modified", 1, "removed", 3, "unchanged", 5));
        assertThat((Integer) snapshot2.read("$.settingsVersion")).isEqualTo(1);
        assertThat((Integer) snapshot1.read("$.settingsVersion")).isZero();
        assertThat((Boolean) snapshot2.read("$.active")).isTrue();
        assertThat((Boolean) client.snapshot(first).read("$.active")).isFalse();
        assertThat(paths(second, ""))
                .containsExactly(
                        "change.txt",
                        "dup1.txt",
                        "dup2.txt",
                        "empty.txt",
                        "keep.txt",
                        SECRET_NAME + ".txt",
                        "moved/b.txt",
                        "new.txt",
                        "vendor/lib.txt");
        assertThat(count(jdbc, "file_contents")).as("v2, new and lib only").isEqualTo(11);
        assertThat(chunkId("keep")).as("unchanged content keeps its chunk").isEqualTo(keepChunk);
        assertThat(paths(first, "")).contains("remove.txt", "move-a.txt", "notes.log");

        String bodies = body(client.files(second, "")) + snapshot2.jsonString();
        assertThat(bodies)
                .doesNotContain(TestFileSystemInitializer.ROOTS_DIR.toString())
                .doesNotContain("test-roots");
        assertThat(output.getAll()).doesNotContain(SECRET_NAME).doesNotContain("change.txt");
    }

    @Test
    void contentThatIsNotIndexableTextIsUnsupported() throws IOException {
        Files.write(tree.resolve("latin1.txt"), "ação".getBytes(StandardCharsets.ISO_8859_1));
        byte[] lateNul = new byte[9000];
        Arrays.fill(lateNul, (byte) 'a');
        lateNul[8500] = 0;
        Files.write(tree.resolve("late-nul.txt"), lateNul);
        write("minified.txt", "x".repeat(4001));
        write("ok.txt", "ok");
        String projectId = project();
        client.putSettings(projectId, "{\"maxFileBytes\": 65536}");

        String snapshotId = client.index(projectId);

        DocumentContext snapshot = client.snapshot(snapshotId);
        assertThat((String) snapshot.read("$.status")).isEqualTo("READY");
        assertThat((Integer) snapshot.read("$.counts.unsupportedFiles")).isEqualTo(3);
        DocumentContext unsupported = json(client.files(snapshotId, "status=UNSUPPORTED"));
        assertThat((List<String>) unsupported.read("$.items[*].reason"))
                .containsExactly("BINARY", "INVALID_ENCODING", "LINE_TOO_LONG");
        assertThat((List<Object>) unsupported.read("$.items[*].contentHash")).containsOnlyNulls();
    }

    @Test
    void chunksMatchTheLinesOfTheStoredContent() throws IOException {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 150; i++) {
            text.append(i % 25 == 0 ? "" : "line " + i).append(i % 2 == 0 ? "\r\n" : "\n");
        }
        write("long.txt", text.toString());
        String snapshotId = client.index(project());

        String content = jdbc.sql("SELECT content FROM file_contents").query(String.class).single();
        assertThat(content).isEqualTo(text.toString());
        List<String> lines = content.lines().toList();
        List<Map<String, Object>> chunks =
                jdbc.sql("SELECT start_line, end_line, content FROM chunks ORDER BY ordinal")
                        .query()
                        .listOfRows();
        assertThat(chunks).hasSizeGreaterThan(2);
        for (Map<String, Object> chunk : chunks) {
            int start = (Integer) chunk.get("start_line");
            int end = (Integer) chunk.get("end_line");
            assertThat(chunk.get("content"))
                    .isEqualTo(String.join("\n", lines.subList(start - 1, end)));
        }
        assertThat((Integer) json(client.files(snapshotId, "")).read("$.items[0].lineCount"))
                .isEqualTo(150);
    }

    @Test
    void zipProjectFollowsTheCurrentImport() {
        UUID projectId = insertZipProject(jdbc, workspaceId);
        String firstImport = upload(projectId, "a.txt", "1", "b.txt", "2");
        String first = client.index(projectId.toString());
        String secondImport = upload(projectId, "a.txt", "1", "c.txt", "3");

        String second = client.index(projectId.toString());

        assertThat((String) client.snapshot(first).read("$.sourceImportId")).isEqualTo(firstImport);
        DocumentContext snapshot = client.snapshot(second);
        assertThat((String) snapshot.read("$.sourceImportId")).isEqualTo(secondImport);
        assertThat((Map<String, Object>) snapshot.read("$.changes"))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("added", 1, "modified", 0, "removed", 1, "unchanged", 1));
    }

    @Test
    void listsSnapshotsAndFiltersFiles() throws IOException {
        write("src/A.java", "class A {}");
        write("src/sub/B.java", "class B {}");
        write("srcx/C.java", "class C {}");
        String projectId = project();
        String first = client.index(projectId);
        String second = client.index(projectId);

        assertThat(mvc.get().uri("/api/v1/projects/{id}/snapshots?size=1", projectId))
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"items": [{"id": "%s", "active": true, "inventory": null}],
                         "totalItems": 2}
                        """
                                .formatted(second));
        assertThat((Integer) client.snapshot(first).read("$.inventory.totals.includedFiles"))
                .isEqualTo(3);
        assertThat(paths(second, "pathPrefix=src")).containsExactly("src/A.java", "src/sub/B.java");
        assertThat(paths(second, "pathPrefix=src/A.java")).containsExactly("src/A.java");
        assertThat(paths(second, "status=FAILED")).isEmpty();
        assertThat(client.files(second, "pathPrefix=../x"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("pathPrefix");
        assertThat(client.files(second, "status=OTHER")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(client.files(UUID.randomUUID().toString(), ""))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("SNAPSHOT_NOT_FOUND");
        assertThat(mvc.get().uri("/api/v1/projects/{id}/snapshots", UUID.randomUUID()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private String project() {
        return client.localProject(workspaceId, "work", tree.getFileName().toString());
    }

    private List<String> paths(String snapshotId, String query) {
        return json(client.files(snapshotId, query)).read("$.items[*].relativePath");
    }

    private String chunkId(String content) {
        return jdbc.sql("SELECT id::text FROM chunks WHERE content_hash = :hash")
                .param("hash", ContentHash.of(content))
                .query(String.class)
                .single();
    }

    private void write(String path, String content) throws IOException {
        Path file = tree.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String upload(UUID projectId, String... namesAndContents) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                out.putNextEntry(new ZipEntry(namesAndContents[i]));
                out.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/projects/{id}/imports/zip", projectId)
                        .multipart()
                        .file(
                                new MockMultipartFile(
                                        "file", "a.zip", "application/zip", bytes.toByteArray()))
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String id = json(result).read("$.id");
        await().atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                json(mvc.get().uri("/api/v1/imports/{id}", id).exchange())
                                        .read("$.status"),
                        "COMPLETED"::equals);
        return id;
    }
}
