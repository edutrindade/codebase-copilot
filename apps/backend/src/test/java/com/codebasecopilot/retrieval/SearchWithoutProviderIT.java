package com.codebasecopilot.retrieval;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// With app.embeddings.provider=none nothing leaves the machine: only LEXICAL answers (D-060).
@IntegrationTest
@TestPropertySource(properties = "app.embeddings.provider=none")
class SearchWithoutProviderIT {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private ObjectProvider<EmbeddingProvider> provider;

    private IndexedProjects projects;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        projects = new IndexedProjects(mvc, insertWorkspace(jdbc));
    }

    @AfterEach
    void removeTrees() throws IOException {
        projects.removeTrees();
    }

    @Test
    void lexicalModeWorksWithoutAProvider() throws IOException {
        UUID projectId = indexedProject();

        MvcTestResult result = search(projectId, "LEXICAL");

        assertThat(provider.getIfAvailable()).isNull();
        assertThat(result).hasStatusOk();
        DocumentContext json = json(result);
        assertThat((List<String>) json.read("$.results[*].path"))
                .containsExactly("src/UserRepository.java");
        assertThat((Object) json.read("$.model")).isNull();
        assertThat((Integer) json.read("$.results[0].ranks.lexical")).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"VECTOR", "HYBRID"})
    void vectorModesAreNotAvailable(String mode) throws IOException {
        UUID projectId = indexedProject();

        MvcTestResult result = search(projectId, mode);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat((String) json(result).read("$.code")).isEqualTo("EMBEDDINGS_NOT_AVAILABLE");
    }

    private UUID indexedProject() throws IOException {
        UUID snapshotId =
                projects.indexedSnapshot(
                        "src/UserRepository.java",
                        "User getUserById(long id);",
                        "src/Billing.java",
                        "void charge();");
        return jdbc.sql("SELECT project_id FROM snapshots WHERE id = :id")
                .param("id", snapshotId)
                .query(UUID.class)
                .single();
    }

    private MvcTestResult search(UUID projectId, String mode) {
        return mvc.post()
                .uri("/api/v1/projects/{id}/search", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\": \"user by id\", \"mode\": \"%s\"}".formatted(mode))
                .exchange();
    }

    private static DocumentContext json(MvcTestResult result) {
        try {
            return JsonPath.parse(result.getResponse().getContentAsString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
