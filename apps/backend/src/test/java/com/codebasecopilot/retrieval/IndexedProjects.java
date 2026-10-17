package com.codebasecopilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Local-folder projects indexed through the API, for the search and answer tests.
public final class IndexedProjects {

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED");

    private final MockMvcTester mvc;
    private final UUID workspaceId;
    private final List<Path> trees = new ArrayList<>();

    public IndexedProjects(MockMvcTester mvc, UUID workspaceId) {
        this.mvc = mvc;
        this.workspaceId = workspaceId;
    }

    public Path tree() throws IOException {
        Path tree =
                Files.createDirectories(
                        TestFileSystemInitializer.ROOTS_DIR.resolve(
                                "work/channel-" + UUID.randomUUID()));
        trees.add(tree);
        return tree;
    }

    public static void write(Path tree, String name, String content) throws IOException {
        Path file = tree.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    public UUID project(Path tree) {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/workspaces/{id}/projects", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name": "P", "sourceType": "LOCAL_FOLDER", "rootKey": "work",
                                 "relativePath": "%s"}
                                """
                                        .formatted(tree.getFileName()))
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(read(result, "$.id"));
    }

    // Runs one index job to completion and returns its snapshot.
    public UUID index(UUID projectId) {
        MvcTestResult created =
                mvc.post().uri("/api/v1/projects/{id}/index-jobs", projectId).exchange();
        assertThat(created).hasStatus(HttpStatus.ACCEPTED);
        String jobId = read(created, "$.id");
        String status =
                await().atMost(Duration.ofSeconds(20))
                        .until(
                                () ->
                                        read(
                                                mvc.get()
                                                        .uri("/api/v1/index-jobs/{id}", jobId)
                                                        .exchange(),
                                                "$.status"),
                                TERMINAL::contains);
        assertThat(status).isEqualTo("COMPLETED");
        return UUID.fromString(read(created, "$.snapshotId"));
    }

    // A new project with these files, indexed once.
    public UUID indexedSnapshot(String... namesAndContents) throws IOException {
        Path tree = tree();
        for (int i = 0; i < namesAndContents.length; i += 2) {
            write(tree, namesAndContents[i], namesAndContents[i + 1]);
        }
        return index(project(tree));
    }

    public void removeTrees() throws IOException {
        for (Path tree : trees) {
            Directories.deleteRecursively(tree);
        }
        trees.clear();
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
