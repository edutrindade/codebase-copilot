package com.codebasecopilot.answering;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.answering.Questions.ask;
import static com.codebasecopilot.answering.Questions.historyRows;
import static com.codebasecopilot.answering.Questions.json;
import static com.codebasecopilot.answering.Questions.projectOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.retrieval.IndexedProjects;
import com.jayway.jsonpath.DocumentContext;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// With app.embeddings.provider=none an answer can still use the lexical search, and the
// default HYBRID does not fall back to it in silence (D-060). No price is configured here.
@IntegrationTest
@TestPropertySource(properties = "app.embeddings.provider=none")
class AnswerWithoutEmbeddingsIT {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

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
    void lexicalModeAnswers() throws IOException {
        UUID projectId = indexedProject();

        MvcTestResult result =
                ask(
                        mvc,
                        projectId,
                        """
                        {"question": "user by id", "options": {"retrievalMode": "LEXICAL"}}
                        """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        DocumentContext json = json(result);
        assertThat((String) json.read("$.status")).isEqualTo("ANSWERED");
        assertThat((String) json.read("$.retrieval.mode")).isEqualTo("LEXICAL");
        assertThat((String) json.read("$.evidence[0].path")).isEqualTo("src/UserRepository.java");
        assertThat((Object) json.read("$.evidence[0].ranks.vector")).isNull();
        assertThat((Integer) json.read("$.metrics.inputTokens")).isPositive();
        assertThat((Object) json.read("$.metrics.estimatedCost")).isNull();
        assertThat((String) json.read("$.metrics.costStatus")).isEqualTo("UNKNOWN");
        assertThat(
                        jdbc.sql("SELECT retrieval_metadata -> 'embeddingModel' FROM answers")
                                .query(String.class)
                                .single())
                .isEqualTo("null");
    }

    @Test
    void hybridModeIsNotAvailable() throws IOException {
        UUID projectId = indexedProject();

        MvcTestResult result = ask(mvc, projectId, "{\"question\": \"user by id\"}");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat((String) json(result).read("$.code")).isEqualTo("EMBEDDINGS_NOT_AVAILABLE");
        assertThat(historyRows(jdbc)).isZero();
    }

    private UUID indexedProject() throws IOException {
        return projectOf(
                jdbc,
                projects.indexedSnapshot(
                        "src/UserRepository.java",
                        "User getUserById(long id);",
                        "src/Billing.java",
                        "void charge();"));
    }
}
