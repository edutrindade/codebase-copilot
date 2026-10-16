package com.codebasecopilot.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
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
class ProjectApiIT {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

    private String workspaceId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        workspaceId = createWorkspace();
    }

    @Test
    void createsZipProject() {
        MvcTestResult result =
                create(workspaceId, "{\"name\": \" Meu ZIP \", \"sourceType\": \"ZIP\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String id = read(result, "$.id");
        assertThat(UUID.fromString(id).version()).isEqualTo(7);
        assertThat(result).hasHeader(HttpHeaders.LOCATION, "/api/v1/projects/" + id);
        assertThat(result)
                .bodyJson()
                .isStrictlyEqualTo(
                        """
                        {
                          "id": "%s",
                          "workspaceId": "%s",
                          "name": "Meu ZIP",
                          "source": {"type": "ZIP", "currentImport": null},
                          "createdAt": "%s"
                        }
                        """
                                .formatted(id, workspaceId, read(result, "$.createdAt")));
    }

    @Test
    void reportsEveryInvalidField() {
        MvcTestResult result =
                create(
                        workspaceId,
                        """
                        {"name": "  ", "sourceType": "ZIP", "rootKey": "default", "relativePath": ""}
                        """);

        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(
                        List.of(
                                Map.of("field", "name", "message", "O nome é obrigatório."),
                                Map.of(
                                        "field",
                                        "relativePath",
                                        "message",
                                        "Não informe este campo para projetos ZIP."),
                                Map.of(
                                        "field",
                                        "rootKey",
                                        "message",
                                        "Não informe este campo para projetos ZIP.")));
    }

    @Test
    void rejectsTooLongNameAndMissingSourceType() {
        assertThat(create(workspaceId, "{\"name\": \"%s\"}".formatted("a".repeat(101))))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .isEqualTo(List.of("name", "sourceType"));
    }

    @Test
    void unknownSourceTypeIsMalformed() {
        assertThat(create(workspaceId, "{\"name\": \"x\", \"sourceType\": \"GIT\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void unknownWorkspaceIsNotFound() {
        assertThat(
                        create(
                                UUID.randomUUID().toString(),
                                "{\"name\": \"x\", \"sourceType\": \"ZIP\"}"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    void invalidBodyWinsOverUnknownWorkspace() {
        assertThat(
                        create(
                                UUID.randomUUID().toString(),
                                "{\"name\": \"\", \"sourceType\": \"ZIP\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void getsProject() {
        String id = createdZip(workspaceId, "Detalhe");

        assertThat(mvc.get().uri("/api/v1/projects/" + id))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"id": "%s", "name": "Detalhe", "source": {"type": "ZIP"}}
                        """
                                .formatted(id));
    }

    @Test
    void unknownOrMalformedProjectId() {
        assertThat(mvc.get().uri("/api/v1/projects/" + UUID.randomUUID()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("PROJECT_NOT_FOUND");
        assertThat(mvc.get().uri("/api/v1/projects/abc"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("projectId");
    }

    @Test
    void listsWorkspaceProjectsNewestFirst() {
        String first = createdZip(workspaceId, "Primeiro");
        String second = createdZip(workspaceId, "Segundo");
        String third = createdZip(workspaceId, "Terceiro");
        createdZip(createWorkspace(), "Outro workspace");

        String uri = "/api/v1/workspaces/" + workspaceId + "/projects";
        assertThat(mvc.get().uri(uri + "?size=2"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"page": 0, "size": 2, "totalItems": 3, "totalPages": 2}
                        """);
        assertThat(mvc.get().uri(uri + "?size=2"))
                .bodyJson()
                .extractingPath("$.items[*].id")
                .isEqualTo(List.of(third, second));
        assertThat(mvc.get().uri(uri + "?page=1&size=2"))
                .bodyJson()
                .extractingPath("$.items[*].id")
                .isEqualTo(List.of(first));
        assertThat(mvc.get().uri(uri + "?size=101")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listingUnknownWorkspaceIsNotFound() {
        assertThat(mvc.get().uri("/api/v1/workspaces/" + UUID.randomUUID() + "/projects"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    void deletesProject() {
        String id = createdZip(workspaceId, "Remover");

        assertThat(mvc.delete().uri("/api/v1/projects/" + id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/v1/projects/" + id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.delete().uri("/api/v1/projects/" + id))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("PROJECT_NOT_FOUND");
    }

    @Test
    void createsLocalFolderProjectWithNormalizedPath() {
        MvcTestResult result = create(workspaceId, local("API", "default", "./repos//api/"));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.source")
                .isEqualTo(
                        Map.of(
                                "type",
                                "LOCAL_FOLDER",
                                "rootKey",
                                "default",
                                "relativePath",
                                "repos/api"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ".", "./", ".//"})
    void rootItselfIsStoredAsEmptyPath(String relativePath) {
        assertThat(create(workspaceId, local("Raiz", "work", relativePath)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.source.relativePath")
                .isEqualTo("");
    }

    @Test
    void creationDoesNotTouchTheFileSystem() {
        assertThat(create(workspaceId, local("Inexistente", "default", "nao/existe/ainda")))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void equivalentPathsInSameWorkspaceAreDuplicates() {
        assertThat(create(workspaceId, local("A", "default", "repo")))
                .hasStatus(HttpStatus.CREATED);

        assertThat(create(workspaceId, local("B", "default", "./repo/")))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("PROJECT_ALREADY_EXISTS");
    }

    @Test
    void duplicatesAreScopedByWorkspaceRootAndCase() {
        assertThat(create(workspaceId, local("A", "default", "repo")))
                .hasStatus(HttpStatus.CREATED);

        assertThat(create(createWorkspace(), local("A", "default", "repo")))
                .hasStatus(HttpStatus.CREATED);
        assertThat(create(workspaceId, local("A", "work", "repo"))).hasStatus(HttpStatus.CREATED);
        assertThat(create(workspaceId, local("A", "default", "Repo")))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void unknownRootIsNotFound() {
        assertThat(create(workspaceId, local("X", "missing", "../x")))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("ROOT_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../x", "/abs", "a/../.."})
    void pathOutsideRootIsRejected(String relativePath) {
        assertThat(create(workspaceId, local("X", "default", relativePath)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("PATH_OUTSIDE_ROOT");
    }

    @Test
    void forbiddenCharactersAreValidationFailure() {
        assertThat(create(workspaceId, local("X", "default", "a\\\\b")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(
                        List.of(
                                Map.of(
                                        "field",
                                        "relativePath",
                                        "message",
                                        "O caminho não pode conter barra invertida nem caracteres"
                                                + " de controle.")));
    }

    @Test
    void localFolderRequiresRootKeyAndRelativePath() {
        assertThat(create(workspaceId, "{\"name\": \"X\", \"sourceType\": \"LOCAL_FOLDER\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .isEqualTo(List.of("relativePath", "rootKey"));
    }

    @Test
    void errorPrecedenceAtEachBoundary() {
        String unknownWorkspace = UUID.randomUUID().toString();
        // 1 vs 2: invalid body before unknown workspace.
        assertCode(create(unknownWorkspace, local("", "missing", "../x")), "VALIDATION_FAILED");
        // 2 vs 4: unknown workspace before unknown root.
        assertCode(create(unknownWorkspace, local("X", "missing", "../x")), "WORKSPACE_NOT_FOUND");
        // 4 vs 5: unknown root before path outside root.
        assertCode(create(workspaceId, local("X", "missing", "../x")), "ROOT_NOT_FOUND");
        // 5 vs 6: path outside root before duplicate.
        assertThat(create(workspaceId, local("X", "default", "a"))).hasStatus(HttpStatus.CREATED);
        assertCode(create(workspaceId, local("X", "default", "a/../..")), "PATH_OUTSIDE_ROOT");
    }

    private static void assertCode(MvcTestResult result, String code) {
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private static String local(String name, String rootKey, String relativePath) {
        return """
                {"name": "%s", "sourceType": "LOCAL_FOLDER", "rootKey": "%s", "relativePath": "%s"}
                """
                .formatted(name, rootKey, relativePath);
    }

    private MvcTestResult create(String workspace, String body) {
        return mvc.post()
                .uri("/api/v1/workspaces/" + workspace + "/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private String createdZip(String workspace, String name) {
        MvcTestResult result =
                create(workspace, "{\"name\": \"%s\", \"sourceType\": \"ZIP\"}".formatted(name));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private String createWorkspace() {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
