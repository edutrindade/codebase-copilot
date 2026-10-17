package com.codebasecopilot.answering;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.answering.Questions.ask;
import static com.codebasecopilot.answering.Questions.body;
import static com.codebasecopilot.answering.Questions.json;
import static com.codebasecopilot.answering.Questions.projectOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.history.AnswerHistory;
import com.codebasecopilot.history.NewQuestion;
import com.codebasecopilot.history.PendingAnswer;
import com.codebasecopilot.providers.GenerationFailure;
import com.codebasecopilot.providers.GenerationProvider;
import com.codebasecopilot.retrieval.IndexedProjects;
import com.jayway.jsonpath.DocumentContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

// The history reads of F5 (Etapa 6): answer, evidence copy and conversations.
@IntegrationTest
class HistoryReadApiIT {

    private static final String SERVICE =
            "class RefundService {\n  long refund(Order order) {\n    return order.total();\n  }\n}\n";
    private static final String QUESTION = "{\"question\": \"How is the refund computed?\"}";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private AnswerHistory history;
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
    void returnsTheAnswerAsCreated() throws IOException {
        UUID projectId = indexedProject();
        MvcTestResult created = ask(mvc, projectId, QUESTION);
        String answerId = json(created).read("$.answerId");

        MvcTestResult result = mvc.get().uri("/api/v1/answers/{id}", answerId).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(body(result)).isEqualTo(body(created));
    }

    @Test
    void rejectsAnUnknownAnswer() {
        MvcTestResult result = mvc.get().uri("/api/v1/answers/{id}", UUID.randomUUID()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat((String) json(result).read("$.code")).isEqualTo("ANSWER_NOT_FOUND");
    }

    @Test
    void returnsTheEvidenceOfAnAvailableSnapshot() throws IOException {
        UUID projectId = indexedProject();
        DocumentContext answer = json(ask(mvc, projectId, QUESTION));
        String evidenceId = answer.read("$.evidence[0].id");

        MvcTestResult result = evidence(projectId, evidenceId);

        assertThat(result).hasStatus(HttpStatus.OK);
        DocumentContext json = json(result);
        assertThat((String) json.read("$.id")).isEqualTo(evidenceId);
        assertThat((String) json.read("$.answerId")).isEqualTo(answer.read("$.answerId"));
        assertThat((String) json.read("$.snapshotId")).isEqualTo(answer.read("$.snapshotId"));
        assertThat((String) json.read("$.snapshotStatus")).isEqualTo("AVAILABLE");
        assertThat((String) json.read("$.citationLabel")).isEqualTo("E1");
        assertThat((String) json.read("$.path")).isEqualTo("src/RefundService.java");
        assertThat((List<?>) json.read("$.otherPaths")).isEmpty();
        assertThat((String) json.read("$.contentHash")).isNotBlank();
        assertThat((Integer) json.read("$.startLine")).isEqualTo(1);
        assertThat((Integer) json.read("$.endLine")).isEqualTo(5);
        assertThat((String) json.read("$.snippet")).isEqualTo(SERVICE.strip());
        assertThat((String) json.read("$.validationStatus")).isEqualTo("CITED");
        assertThat((Integer) json.read("$.rank")).isEqualTo(1);
        assertThat((Integer) json.read("$.ranks.lexical")).isEqualTo(1);
        assertThat((String) json.read("$.activeVersion")).isEqualTo("SAME");
    }

    @Test
    void comparesTheEvidenceWithTheActiveSnapshot() throws IOException {
        Path tree = projects.tree();
        IndexedProjects.write(tree, "src/RefundService.java", SERVICE);
        IndexedProjects.write(tree, "README.md", "project notes\n");
        UUID projectId = projects.project(tree);
        projects.index(projectId);
        String evidenceId = json(ask(mvc, projectId, QUESTION)).read("$.evidence[0].id");

        IndexedProjects.write(tree, "src/RefundService.java", SERVICE + "// changed\n");
        projects.index(projectId);
        assertThat(activeVersion(projectId, evidenceId)).isEqualTo("CHANGED");

        Files.delete(tree.resolve("src/RefundService.java"));
        projects.index(projectId);
        assertThat(activeVersion(projectId, evidenceId)).isEqualTo("REMOVED");

        jdbc.sql("UPDATE projects SET active_snapshot_id = NULL WHERE id = :id")
                .param("id", projectId)
                .update();
        assertThat(activeVersion(projectId, evidenceId)).isEqualTo("NO_ACTIVE_SNAPSHOT");
    }

    @Test
    void returnsTheCopyOfAPrunedSnapshot() throws IOException {
        UUID projectId = indexedProject();
        DocumentContext answer = json(ask(mvc, projectId, QUESTION));
        String evidenceId = answer.read("$.evidence[0].id");
        jdbc.sql("UPDATE snapshots SET pruned_at = now() WHERE id = :id")
                .param("id", UUID.fromString(answer.read("$.snapshotId")))
                .update();
        jdbc.sql("DELETE FROM chunks").update();

        MvcTestResult result = evidence(projectId, evidenceId);

        assertThat(result).hasStatus(HttpStatus.OK);
        DocumentContext json = json(result);
        assertThat((String) json.read("$.snapshotStatus")).isEqualTo("PRUNED");
        assertThat((String) json.read("$.path")).isEqualTo("src/RefundService.java");
        assertThat((String) json.read("$.snippet")).isEqualTo(SERVICE.strip());
    }

    @Test
    void rejectsEvidenceOfAnotherProject() throws IOException {
        UUID projectId = indexedProject();
        String evidenceId = json(ask(mvc, projectId, QUESTION)).read("$.evidence[0].id");
        UUID otherProject = indexedProject();

        MvcTestResult result = evidence(otherProject, evidenceId);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat((String) json(result).read("$.code")).isEqualTo("EVIDENCE_NOT_FOUND");
    }

    @Test
    void rejectsEvidenceOfAnUnknownProjectFirst() throws IOException {
        UUID projectId = indexedProject();
        String evidenceId = json(ask(mvc, projectId, QUESTION)).read("$.evidence[0].id");

        assertThat((String) json(evidence(UUID.randomUUID(), evidenceId)).read("$.code"))
                .isEqualTo("PROJECT_NOT_FOUND");
        assertThat((String) json(evidence(projectId, UUID.randomUUID().toString())).read("$.code"))
                .isEqualTo("EVIDENCE_NOT_FOUND");
    }

    @Test
    void listsConversationsByLatestQuestionWithPages() throws IOException {
        UUID projectId = indexedProject();
        String first = conversationOf(ask(mvc, projectId, question("First refund?")));
        String second = conversationOf(ask(mvc, projectId, question("Second refund?")));
        String third = conversationOf(ask(mvc, projectId, question("Third refund?")));
        ask(mvc, projectId, followUp("And the total?", first));
        ask(mvc, indexedProject(), QUESTION);

        DocumentContext page0 = json(conversations(projectId, "page=0&size=2"));
        DocumentContext page1 = json(conversations(projectId, "page=1&size=2"));

        assertThat((List<String>) page0.read("$.items[*].id")).containsExactly(first, third);
        assertThat((List<String>) page1.read("$.items[*].id")).containsExactly(second);
        assertThat((Integer) page0.read("$.totalItems")).isEqualTo(3);
        assertThat((Integer) page0.read("$.totalPages")).isEqualTo(2);
        assertThat((Integer) page0.read("$.size")).isEqualTo(2);
        assertThat((String) page0.read("$.items[0].title")).isEqualTo("First refund?");
        assertThat((Integer) page0.read("$.items[0].questionCount")).isEqualTo(2);
        assertThat((Integer) page0.read("$.items[1].questionCount")).isEqualTo(1);
        assertThat((String) page0.read("$.items[0].projectId")).isEqualTo(projectId.toString());
        assertThat((List<?>) json(conversations(projectId, "page=5")).read("$.items")).isEmpty();
    }

    @Test
    void rejectsConversationListsOfUnknownProjectsAndBadPages() {
        MvcTestResult unknown = conversations(UUID.randomUUID(), "");
        MvcTestResult badSize = conversations(UUID.randomUUID(), "size=101");

        assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
        assertThat((String) json(unknown).read("$.code")).isEqualTo("PROJECT_NOT_FOUND");
        assertThat(badSize).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat((String) json(badSize).read("$.code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void returnsAConversationWithAnsweredFailedAndPendingAnswersInOrder() throws IOException {
        UUID projectId = indexedProject();
        DocumentContext answered = json(ask(mvc, projectId, question("First refund?")));
        String conversationId = answered.read("$.conversationId");
        doThrow(new GenerationFailure(GenerationFailure.Type.UNAVAILABLE, "detail"))
                .when(generator)
                .generate(any());
        ask(mvc, projectId, followUp("Second refund?", conversationId));
        PendingAnswer pending =
                history.start(
                        new NewQuestion(
                                projectId,
                                UUID.fromString(conversationId),
                                "Third refund?",
                                UUID.fromString(answered.read("$.snapshotId")),
                                "HYBRID",
                                "prompt-v1"));

        MvcTestResult result =
                mvc.get().uri("/api/v1/conversations/{id}", conversationId).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        DocumentContext json = json(result);
        assertThat((String) json.read("$.conversation.id")).isEqualTo(conversationId);
        assertThat((String) json.read("$.conversation.title")).isEqualTo("First refund?");
        assertThat((Integer) json.read("$.conversation.questionCount")).isEqualTo(3);
        assertThat((List<String>) json.read("$.answers[*].question"))
                .containsExactly("First refund?", "Second refund?", "Third refund?");
        assertThat((List<String>) json.read("$.answers[*].status"))
                .containsExactly("ANSWERED", "FAILED", "PENDING");
        assertThat((String) json.read("$.answers[0].answerId"))
                .isEqualTo(answered.read("$.answerId"));
        assertThat((List<String>) json.read("$.answers[0].evidence[*].citationLabel"))
                .containsExactly("E1");
        assertThat((String) json.read("$.answers[1].error.code"))
                .isEqualTo("GENERATION_PROVIDER_UNAVAILABLE");
        assertThat((List<String>) json.read("$.answers[1].evidence[*].validationStatus"))
                .containsExactly("NOT_CITED");
        assertThat((String) json.read("$.answers[2].answerId"))
                .isEqualTo(pending.answerId().toString());
        assertThat((Object) json.read("$.answers[2].answer")).isNull();
        assertThat((Object) json.read("$.answers[2].completedAt")).isNull();
        assertThat((List<?>) json.read("$.answers[2].evidence")).isEmpty();
        assertThat((Object) json.read("$.answers[0].answer")).isNotNull();
    }

    @Test
    void rejectsAnUnknownConversation() {
        MvcTestResult result =
                mvc.get().uri("/api/v1/conversations/{id}", UUID.randomUUID()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat((String) json(result).read("$.code")).isEqualTo("CONVERSATION_NOT_FOUND");
    }

    private UUID indexedProject() throws IOException {
        return projectOf(jdbc, projects.indexedSnapshot("src/RefundService.java", SERVICE));
    }

    private String activeVersion(UUID projectId, String evidenceId) {
        return json(evidence(projectId, evidenceId)).read("$.activeVersion");
    }

    private MvcTestResult evidence(UUID projectId, String evidenceId) {
        return mvc.get().uri("/api/v1/projects/{p}/evidence/{e}", projectId, evidenceId).exchange();
    }

    private MvcTestResult conversations(UUID projectId, String query) {
        return mvc.get().uri("/api/v1/projects/{p}/conversations?" + query, projectId).exchange();
    }

    private static String question(String text) {
        return "{\"question\": \"%s\"}".formatted(text);
    }

    private static String followUp(String text, String conversationId) {
        return "{\"question\": \"%s\", \"conversationId\": \"%s\"}".formatted(text, conversationId);
    }

    private static String conversationOf(MvcTestResult result) {
        return json(result).read("$.conversationId");
    }
}
