package com.codebasecopilot.answering;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.IndexRows.insertIndexJob;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static com.codebasecopilot.answering.Questions.ask;
import static com.codebasecopilot.answering.Questions.json;
import static com.codebasecopilot.answering.Questions.projectOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.providers.GenerationFailure;
import com.codebasecopilot.providers.GenerationProvider;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// GET /projects/{id}/usage (P7 of Etapa 8): aggregates over index_jobs and answers.
@IntegrationTest
class UsageApiIT {

    private static final String SERVICE =
            "class RefundService {\n  long refund(Order order) {\n    return order.total();\n  }\n}\n";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @MockitoSpyBean private GenerationProvider generator;

    private IndexedProjects projects;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        reset(generator);
        projects = new IndexedProjects(mvc, insertWorkspace(jdbc));
    }

    @AfterEach
    void tearDown() throws IOException {
        reset(generator);
        projects.removeTrees();
    }

    @Test
    void projectWithoutJobsOrQuestionsHasZeroUsage() throws IOException {
        UUID projectId = projects.project(projects.tree());

        assertThat(usage(projectId))
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"indexing": {"total": 0, "completed": 0, "failed": 0, "cancelled": 0,
                                      "averageDurationMs": null, "p95DurationMs": null},
                         "questions": {"total": 0, "answered": 0, "partial": 0, "abstained": 0,
                                       "failed": 0, "pending": 0, "p50LatencyMs": null,
                                       "p95LatencyMs": null},
                         "cost": {"inputTokens": 0, "outputTokens": 0, "estimatedCost": null,
                                  "currency": null, "unknownCostAnswers": 0}}
                        """);
    }

    @Test
    void aggregatesJobsAnswersTokensAndCost() throws IOException {
        UUID projectId =
                projectOf(jdbc, projects.indexedSnapshot("src/RefundService.java", SERVICE));
        insertIndexJob(jdbc, projectId, insertSnapshot(jdbc, projectId, "FAILED"), "FAILED");
        String estimated =
                json(ask(mvc, projectId, "{\"question\": \"How is the refund computed?\"}"))
                        .read("$.answerId");
        ask(mvc, projectId, "{\"question\": \"Where is the refund?\"}");
        ask(mvc, projectId, "{\"question\": \"zzzz qqqq\"}");
        doThrow(new GenerationFailure(GenerationFailure.Type.UNAVAILABLE, "detail"))
                .when(generator)
                .generate(any());
        ask(mvc, projectId, "{\"question\": \"Refund again?\"}");
        jdbc.sql(
                        """
                        UPDATE answers
                        SET cost_status = 'ESTIMATED', estimated_cost = 0.0125,
                            cost_currency = 'USD'
                        WHERE id = :id
                        """)
                .param("id", UUID.fromString(estimated))
                .update();
        long tokens =
                jdbc.sql("SELECT coalesce(sum(input_tokens), 0) FROM answers")
                        .query(Long.class)
                        .single();

        DocumentContext json = json(usage(projectId));

        assertThat((Integer) json.read("$.indexing.total")).isEqualTo(2);
        assertThat((Integer) json.read("$.indexing.completed")).isEqualTo(1);
        assertThat((Integer) json.read("$.indexing.failed")).isEqualTo(1);
        assertThat((Object) json.read("$.indexing.averageDurationMs")).isNotNull();
        assertThat((Object) json.read("$.indexing.p95DurationMs")).isNotNull();
        assertThat((Integer) json.read("$.questions.total")).isEqualTo(4);
        assertThat((Integer) json.read("$.questions.failed")).isEqualTo(1);
        assertThat(
                        (Integer) json.read("$.questions.answered")
                                + (Integer) json.read("$.questions.partial")
                                + (Integer) json.read("$.questions.abstained"))
                .isEqualTo(3);
        assertThat((Object) json.read("$.questions.p50LatencyMs")).isNotNull();
        assertThat(((Number) json.read("$.cost.inputTokens")).longValue()).isEqualTo(tokens);
        assertThat((Double) json.read("$.cost.estimatedCost")).isEqualTo(0.0125);
        assertThat((String) json.read("$.cost.currency")).isEqualTo("USD");
        assertThat((Integer) json.read("$.cost.unknownCostAnswers"))
                .isEqualTo(
                        jdbc.sql(
                                        """
                                        SELECT count(*) FROM answers
                                        WHERE generation_model IS NOT NULL
                                          AND cost_status = 'UNKNOWN'
                                        """)
                                .query(Integer.class)
                                .single());
    }

    @Test
    void removedConversationsLeaveTheAggregates() throws IOException {
        UUID projectId =
                projectOf(jdbc, projects.indexedSnapshot("src/RefundService.java", SERVICE));
        String conversationId =
                json(ask(mvc, projectId, "{\"question\": \"How is the refund computed?\"}"))
                        .read("$.conversationId");
        ask(mvc, projectId, "{\"question\": \"Where is the refund?\"}");

        mvc.delete().uri("/api/v1/conversations/{id}", conversationId).exchange();

        assertThat((Integer) json(usage(projectId)).read("$.questions.total")).isEqualTo(1);
    }

    @Test
    void rejectsAnUnknownProject() {
        MvcTestResult result = usage(UUID.randomUUID());

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat((String) json(result).read("$.code")).isEqualTo("PROJECT_NOT_FOUND");
    }

    private MvcTestResult usage(UUID projectId) {
        return mvc.get().uri("/api/v1/projects/{id}/usage", projectId).exchange();
    }
}
