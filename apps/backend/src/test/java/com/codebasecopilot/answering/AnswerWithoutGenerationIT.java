package com.codebasecopilot.answering;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.answering.Questions.ask;
import static com.codebasecopilot.answering.Questions.historyRows;
import static com.codebasecopilot.answering.Questions.json;
import static com.codebasecopilot.answering.Questions.projectOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.providers.GenerationProvider;
import com.codebasecopilot.retrieval.IndexedProjects;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// With app.generation.provider=none nothing is sent and nothing is persisted; the checks of the
// request and of the snapshot still come first.
@IntegrationTest
@TestPropertySource(properties = "app.generation.provider=none")
class AnswerWithoutGenerationIT {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private ObjectProvider<GenerationProvider> generator;

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
    void questionsAreNotAvailable() throws IOException {
        UUID projectId = projectOf(jdbc, projects.indexedSnapshot("a.txt", "refund"));

        MvcTestResult result = ask(mvc, projectId, "{\"question\": \"refund\"}");

        assertThat(generator.getIfAvailable()).isNull();
        assertError(result, HttpStatus.CONFLICT, "GENERATION_NOT_AVAILABLE");
        assertThat(historyRows(jdbc)).isZero();
    }

    @Test
    void requestAndSnapshotChecksComeFirst() throws IOException {
        UUID projectId = projects.project(projects.tree());

        assertError(
                ask(mvc, projectId, "{\"question\": \"\"}"),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED");
        assertError(
                ask(mvc, UUID.randomUUID(), "{\"question\": \"refund\"}"),
                HttpStatus.NOT_FOUND,
                "PROJECT_NOT_FOUND");
        assertError(
                ask(mvc, projectId, "{\"question\": \"refund\"}"),
                HttpStatus.CONFLICT,
                "SNAPSHOT_NOT_SEARCHABLE");
    }

    @Test
    void lexicalMismatchComesFirst() throws IOException {
        UUID projectId = projectOf(jdbc, projects.indexedSnapshot("a.txt", "refund"));
        jdbc.sql("UPDATE snapshots SET lexical_version = NULL").update();

        assertError(
                ask(mvc, projectId, "{\"question\": \"refund\"}"),
                HttpStatus.CONFLICT,
                "SNAPSHOT_LEXICAL_MISMATCH");
    }

    private static void assertError(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat((String) json(result).read("$.code")).isEqualTo(code);
    }
}
