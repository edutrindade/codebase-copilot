package com.codebasecopilot.importjob.scan;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class ProjectSettingsApiIT {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        projectId = insertZipProject(jdbc, insertWorkspace(jdbc));
    }

    @Test
    void projectWithoutSettingsShowsDefaults() {
        assertThat(mvc.get().uri("/api/v1/projects/{id}/settings", projectId))
                .hasStatusOk()
                .bodyJson()
                .satisfies(
                        json -> {
                            assertThat(json)
                                    .isLenientlyEqualTo(
                                            """
                                            {
                                              "excludePatterns": [],
                                              "disabledDefaultRules": [],
                                              "maxFileBytes": null,
                                              "maxProjectBytes": null,
                                              "effectiveMaxFileBytes": 4096,
                                              "effectiveMaxProjectBytes": 524288000,
                                              "version": 0,
                                              "updatedAt": null
                                            }
                                            """);
                            assertThat(json)
                                    .extractingPath("$.defaultRules.length()")
                                    .isEqualTo(DefaultExclusionRules.ALL.size());
                            assertThat(json)
                                    .extractingPath("$.defaultRules[0]")
                                    .isEqualTo(
                                            Map.of(
                                                    "id",
                                                    "env-files",
                                                    "category",
                                                    "SENSITIVE",
                                                    "patterns",
                                                    List.of(".env", ".env.*"),
                                                    "exceptions",
                                                    List.of(
                                                            ".env.example",
                                                            ".env.sample",
                                                            ".env.template"),
                                                    "overridable",
                                                    false,
                                                    "disabled",
                                                    false));
                        });
    }

    @Test
    void savesSettingsAndIncrementsVersion() {
        MvcTestResult first =
                put(
                        """
                        {"excludePatterns": ["docs/generated/", "*.log", "*.log"],
                         "disabledDefaultRules": ["vendor", "vendor"],
                         "maxFileBytes": 2048, "maxProjectBytes": null}
                        """);

        assertThat(first)
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {
                          "excludePatterns": ["docs/generated/", "*.log"],
                          "disabledDefaultRules": ["vendor"],
                          "maxFileBytes": 2048,
                          "maxProjectBytes": null,
                          "effectiveMaxFileBytes": 2048,
                          "effectiveMaxProjectBytes": 524288000,
                          "version": 1
                        }
                        """);
        assertThat(first)
                .bodyJson()
                .extractingPath("$.defaultRules[?(@.id == 'vendor')].disabled")
                .isEqualTo(List.of(true));

        assertThat(put("{}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"excludePatterns": [], "disabledDefaultRules": [], "maxFileBytes": null,
                         "effectiveMaxFileBytes": 4096, "version": 2}
                        """);
        assertThat(mvc.get().uri("/api/v1/projects/{id}/settings", projectId))
                .bodyJson()
                .extractingPath("$.version")
                .isEqualTo(2);
    }

    @Test
    void reportsEveryInvalidFieldWithoutRejectedValues() throws UnsupportedEncodingException {
        MvcTestResult result =
                put(
                        """
                        {"excludePatterns": ["ok/", "!SECRET-PATTERN", null, "../x"],
                         "disabledDefaultRules": ["env-files", "vcs", "nope", null, "vendor"],
                         "maxFileBytes": 100, "maxProjectBytes": 999999999999}
                        """);

        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .isEqualTo(
                        List.of(
                                "disabledDefaultRules[0]",
                                "disabledDefaultRules[1]",
                                "disabledDefaultRules[2]",
                                "disabledDefaultRules[3]",
                                "excludePatterns[1]",
                                "excludePatterns[2]",
                                "excludePatterns[3]",
                                "maxFileBytes",
                                "maxProjectBytes"));
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("SECRET-PATTERN")
                .doesNotContain("999999999999");
        assertThat(mvc.get().uri("/api/v1/projects/{id}/settings", projectId))
                .bodyJson()
                .extractingPath("$.version")
                .isEqualTo(0);
    }

    @Test
    void rejectsTooManyPatterns() {
        String patterns =
                String.join(",", IntStream.range(0, 101).mapToObj(i -> "\"p" + i + "\"").toList());

        assertThat(put("{\"excludePatterns\": [" + patterns + "]}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .isEqualTo(List.of("excludePatterns"));
    }

    @Test
    void unknownProjectIsNotFoundAfterValidation() {
        projectId = UUID.randomUUID();

        assertCode(
                mvc.get().uri("/api/v1/projects/{id}/settings", projectId).exchange(),
                "PROJECT_NOT_FOUND");
        assertCode(put("{}"), "PROJECT_NOT_FOUND");
        assertCode(put("{\"excludePatterns\": [\"!x\"]}"), "VALIDATION_FAILED");
    }

    @Test
    void deletingProjectRemovesSettings() {
        assertThat(put("{\"excludePatterns\": [\"*.log\"]}")).hasStatusOk();

        assertThat(mvc.delete().uri("/api/v1/projects/{id}", projectId))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(jdbc.sql("SELECT count(*) FROM project_settings").query(Long.class).single())
                .isZero();
    }

    private MvcTestResult put(String body) {
        return mvc.put()
                .uri("/api/v1/projects/{id}/settings", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static void assertCode(MvcTestResult result, String code) {
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
