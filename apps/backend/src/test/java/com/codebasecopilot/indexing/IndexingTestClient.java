package com.codebasecopilot.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// API calls shared by the indexing integration tests.
final class IndexingTestClient {

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED");

    private final MockMvcTester mvc;

    IndexingTestClient(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    String localProject(UUID workspaceId, String rootKey, String relativePath) {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/workspaces/{id}/projects", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name": "P", "sourceType": "LOCAL_FOLDER", "rootKey": "%s",
                                 "relativePath": "%s"}
                                """
                                        .formatted(rootKey, relativePath))
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return json(result).read("$.id");
    }

    void putSettings(String projectId, String body) {
        assertThat(
                        mvc.put()
                                .uri("/api/v1/projects/{id}/settings", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .hasStatusOk();
    }

    // Runs one index job to completion and returns its snapshot id.
    String index(String projectId) {
        DocumentContext job = runJob(projectId);
        assertThat((String) job.read("$.status"))
                .as("job %s", job.jsonString())
                .isEqualTo("COMPLETED");
        return job.read("$.snapshotId");
    }

    // Runs one index job until it ends, successfully or not, and returns it.
    DocumentContext runJob(String projectId) {
        MvcTestResult created =
                mvc.post().uri("/api/v1/projects/{id}/index-jobs", projectId).exchange();
        assertThat(created).hasStatus(HttpStatus.ACCEPTED);
        String jobId = json(created).read("$.id");
        await().atMost(Duration.ofSeconds(20))
                .until(() -> job(jobId).read("$.status"), TERMINAL::contains);
        return job(jobId);
    }

    DocumentContext job(String jobId) {
        return json(mvc.get().uri("/api/v1/index-jobs/{id}", jobId).exchange());
    }

    DocumentContext snapshot(String snapshotId) {
        MvcTestResult result = mvc.get().uri("/api/v1/snapshots/{id}", snapshotId).exchange();
        assertThat(result).hasStatusOk();
        return json(result);
    }

    MvcTestResult files(String snapshotId, String query) {
        return mvc.get()
                .uri("/api/v1/snapshots/" + snapshotId + "/files?size=100&" + query)
                .exchange();
    }

    static DocumentContext json(MvcTestResult result) {
        return JsonPath.parse(body(result));
    }

    static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
