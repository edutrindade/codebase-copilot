package com.codebasecopilot.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class LocalAccessFilterIT {

    @Autowired private MockMvcTester mvc;
    @Autowired private Environment environment;

    @Test
    void listensOnLoopbackByDefault() {
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    void loopbackHostsAreServed() {
        for (String host :
                new String[] {"localhost:8080", "127.0.0.1:8080", "[::1]:8080", "LOCALHOST"}) {
            assertThat(mvc.get().uri("/api/v1/workspaces").header(HttpHeaders.HOST, host))
                    .as(host)
                    .hasStatusOk();
        }
    }

    @Test
    void otherHostsAreRejectedAsProblems() {
        for (String host :
                new String[] {
                    "evil.example",
                    "evil.example:8080",
                    "192.168.0.10:8080",
                    "localhost.evil.example"
                }) {
            MvcTestResult result =
                    mvc.get().uri("/api/v1/workspaces").header(HttpHeaders.HOST, host).exchange();

            assertThat(result)
                    .as(host)
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson()
                    .isLenientlyEqualTo(
                            """
                            {"code": "FORBIDDEN_HOST", "title": "Endereço não permitido",
                             "requestId": "%s"}
                            """
                                    .formatted(
                                            result.getResponse()
                                                    .getHeader(RequestIdFilter.HEADER)));
        }
    }

    @Test
    void stateChangesFromOtherOriginsAreRejected() {
        for (String origin :
                new String[] {"https://evil.example", "http://localhost:3000", "null"}) {
            MvcTestResult result = createWorkspace(origin);

            assertThat(result).as(origin).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN_ORIGIN");
        }
        assertThat(
                        mvc.delete()
                                .uri(
                                        "/api/v1/workspaces/{id}",
                                        "0192f5c4-7e3a-7b1e-9c3d-2f4a6b8c0d1e")
                                .header(HttpHeaders.ORIGIN, "https://evil.example"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void theUiOriginsAndRequestsWithoutOriginPass() {
        for (String origin :
                new String[] {
                    "http://localhost:5173", "http://127.0.0.1:4173", "HTTP://LOCALHOST:5173"
                }) {
            assertThat(createWorkspace(origin)).as(origin).hasStatus(HttpStatus.CREATED);
        }
        assertThat(createWorkspace(null)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void readsFromOtherOriginsAreLeftToTheBrowser() {
        assertThat(
                        mvc.get()
                                .uri("/api/v1/workspaces")
                                .header(HttpHeaders.ORIGIN, "https://evil.example"))
                .hasStatusOk();
    }

    private MvcTestResult createWorkspace(String origin) {
        var request =
                mvc.post()
                        .uri("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}");
        if (origin != null) {
            request = request.header(HttpHeaders.ORIGIN, origin);
        }
        return request.exchange();
    }
}
