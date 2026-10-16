package com.codebasecopilot.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTestWithoutLocalRoots;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTestWithoutLocalRoots
class ProjectWithoutLocalRootsApiIT {

    private static final String LOCAL =
            """
            {"name": "X", "sourceType": "LOCAL_FOLDER", "rootKey": "default", "relativePath": "a"}
            """;

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

    private String workspaceId;

    @BeforeEach
    void setUp() throws UnsupportedEncodingException {
        DatabaseCleaner.truncateAll(jdbc);
        MvcTestResult workspace =
                mvc.post()
                        .uri("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .exchange();
        workspaceId = JsonPath.read(workspace.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void localFolderIsRejectedWhenNoRootIsConfigured() {
        assertThat(create(workspaceId, LOCAL))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("LOCAL_ROOTS_NOT_CONFIGURED");
    }

    @Test
    void unknownWorkspaceWinsOverMissingRoots() {
        assertThat(create(UUID.randomUUID().toString(), LOCAL))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    void zipStillWorks() {
        assertThat(create(workspaceId, "{\"name\": \"Z\", \"sourceType\": \"ZIP\"}"))
                .hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult create(String workspace, String body) {
        return mvc.post()
                .uri("/api/v1/workspaces/" + workspace + "/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
