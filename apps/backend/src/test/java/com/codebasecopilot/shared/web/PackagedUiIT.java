package com.codebasecopilot.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// The UI served by the backend (D-085), with the stand-in in src/test/resources/static.
@IntegrationTest
class PackagedUiIT {

    private static final String MARKER = "packaged-ui-marker";

    @Autowired private MockMvcTester mvc;

    // Boot's welcome page forwards the root to index.html, which the resource handler serves.
    @Test
    void forwardsTheRootToIndex() {
        assertThat(mvc.get().uri("/")).hasStatusOk().hasForwardedUrl("index.html");
    }

    @Test
    void servesIndexForClientRoutes() {
        for (String path :
                new String[] {
                    "/index.html",
                    "/workspaces/0192f5c4-7e3a-7b1e-9c3d-2f4a6b8c0d1e",
                    "/projects/0192f5c4-7e3a-7b1e-9c3d-2f4a6b8c0d1e/conversations/abc",
                    "/rota-desconhecida"
                }) {
            MvcTestResult result = mvc.get().uri(path).exchange();

            assertThat(result).as(path).hasStatusOk();
            assertThat(result).as(path).hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
            assertThat(result).as(path).bodyText().contains(MARKER);
            assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                    .as(path)
                    .isEqualTo("no-cache");
        }
    }

    @Test
    void servesHashedAssetsWithALongCache() {
        MvcTestResult result = mvc.get().uri("/assets/app-test.js").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().contains("packaged-ui-asset");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .contains("max-age=31536000")
                .contains("immutable");
    }

    @Test
    void missingFilesApiAndActuatorPathsStayProblems() {
        for (String path :
                new String[] {
                    "/assets/missing.js",
                    "/favicon.ico",
                    "/api/v1/unknown",
                    "/api",
                    "/actuator/unknown"
                }) {
            MvcTestResult result = mvc.get().uri(path).exchange();

            assertThat(result).as(path).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).as(path).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).as(path).bodyText().doesNotContain(MARKER);
        }
    }

    @Test
    void everyResponseCarriesTheSecurityHeaders() {
        for (String path :
                new String[] {"/", "/projects/x", "/api/v1/workspaces", "/api/v1/unknown"}) {
            MvcTestResult result = mvc.get().uri(path).exchange();

            assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                    .as(path)
                    .isEqualTo(SecurityHeadersFilter.CONTENT_SECURITY_POLICY)
                    .contains("frame-ancestors 'none'")
                    .contains("script-src 'self'");
            assertThat(result.getResponse().getHeader("X-Content-Type-Options"))
                    .isEqualTo("nosniff");
            assertThat(result.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        }
        MvcTestResult rejected =
                mvc.get().uri("/").header(HttpHeaders.HOST, "evil.example").exchange();
        assertThat(rejected).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(rejected.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void swaggerUiStillLoadsWithoutTheStrictPolicy() {
        MvcTestResult result = mvc.get().uri("/swagger-ui/index.html").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                .isEqualTo(SecurityHeadersFilter.SWAGGER_POLICY);
    }
}
