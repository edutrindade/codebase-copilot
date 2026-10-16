package com.codebasecopilot.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class WorkspaceApiIT {

    private static final String BASE = "/api/v1/workspaces";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void createsWorkspace() throws Exception {
        MvcTestResult result = create("{\"name\": \"  Meus repos \"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String body = result.getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(id.version()).isEqualTo(7);
        assertThat(result).hasHeader(HttpHeaders.LOCATION, BASE + "/" + id);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Meus repos");
        Instant createdAt = Instant.parse(JsonPath.read(body, "$.createdAt"));
        assertThat(createdAt).isBefore(Instant.now().plusSeconds(1));
        assertThat(result)
                .bodyJson()
                .isStrictlyEqualTo(
                        """
                        {"id": "%s", "name": "Meus repos", "projectCount": 0, "createdAt": "%s"}
                        """
                                .formatted(id, JsonPath.read(body, "$.createdAt")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\": null}", "{\"name\": \"   \"}"})
    void usesDefaultName(String body) {
        assertThat(create(body))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("Meus projetos");
    }

    @Test
    void rejectsTooLongName() {
        assertValidationError(
                create("{\"name\": \"%s\"}".formatted("a".repeat(101))),
                "O nome deve ter no máximo 100 caracteres.");
    }

    @Test
    void rejectsControlCharacters() {
        assertValidationError(
                create("{\"name\": \"a\\u0007b\"}"),
                "O nome não pode conter caracteres de controle.");
    }

    @Test
    void getsWorkspaceById() {
        String id = createdId("Detalhe");

        assertThat(mvc.get().uri(BASE + "/" + id))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("Detalhe");
    }

    @Test
    void unknownWorkspaceIsNotFound() {
        assertThat(mvc.get().uri(BASE + "/" + UUID.randomUUID()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    void malformedIdIsValidationFailure() {
        assertThat(mvc.get().uri(BASE + "/not-a-uuid"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("workspaceId");
    }

    @Test
    void listsNewestFirstWithPagination() {
        String first = createdId("Primeiro");
        String second = createdId("Segundo");
        String third = createdId("Terceiro");

        assertThat(mvc.get().uri(BASE + "?page=0&size=2"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"page": 0, "size": 2, "totalItems": 3, "totalPages": 2}
                        """);
        assertThat(mvc.get().uri(BASE + "?page=0&size=2"))
                .bodyJson()
                .extractingPath("$.items[*].id")
                .isEqualTo(List.of(third, second));
        assertThat(mvc.get().uri(BASE + "?page=1&size=2"))
                .bodyJson()
                .extractingPath("$.items[*].id")
                .isEqualTo(List.of(first));
    }

    @Test
    void listUsesDefaultsAndReturnsEmptyPageBeyondTheLast() {
        createdId("Único");

        assertThat(mvc.get().uri(BASE))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"page": 0, "size": 20, "totalItems": 1, "totalPages": 1}
                        """);
        assertThat(mvc.get().uri(BASE + "?page=5"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.items")
                .isEqualTo(List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=0", "size=101", "page=-1"})
    void rejectsPaginationOutOfRange(String query) {
        assertThat(mvc.get().uri(BASE + "?" + query))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void deletesWorkspace() {
        String id = createdId("Remover");

        assertThat(mvc.delete().uri(BASE + "/" + id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri(BASE + "/" + id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.delete().uri(BASE + "/" + id))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    void countsProjectsInListAndDetail() {
        String empty = createdId("Vazio");
        String two = createdId("Dois");
        String one = createdId("Um");
        createZipProject(two);
        createZipProject(two);
        createZipProject(one);

        assertThat(mvc.get().uri(BASE))
                .bodyJson()
                .extractingPath("$.items[*].projectCount")
                .isEqualTo(List.of(1, 2, 0));
        assertThat(mvc.get().uri(BASE + "/" + two))
                .bodyJson()
                .extractingPath("$.projectCount")
                .isEqualTo(2);
        assertThat(mvc.get().uri(BASE + "/" + empty))
                .bodyJson()
                .extractingPath("$.projectCount")
                .isEqualTo(0);
    }

    @Test
    void workspaceWithProjectsCannotBeDeleted() throws Exception {
        String id = createdId("Com projeto");
        String projectId = createZipProject(id);

        assertThat(mvc.delete().uri(BASE + "/" + id))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("WORKSPACE_NOT_EMPTY");
        assertThat(mvc.get().uri(BASE + "/" + id)).hasStatusOk();

        assertThat(mvc.delete().uri("/api/v1/projects/" + projectId))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri(BASE + "/" + id)).hasStatus(HttpStatus.NO_CONTENT);
    }

    private String createZipProject(String workspaceId) {
        MvcTestResult result =
                mvc.post()
                        .uri(BASE + "/" + workspaceId + "/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"P\", \"sourceType\": \"ZIP\"}")
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult create(String body) {
        return mvc.post()
                .uri(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private String createdId(String name) {
        MvcTestResult result = create("{\"name\": \"%s\"}".formatted(name));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertValidationError(MvcTestResult result, String message) {
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(List.of(Map.of("field", "name", "message", message)));
    }
}
