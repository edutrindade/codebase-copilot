package com.codebasecopilot.history;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class HistorySchemaIT {

    @Autowired private JdbcClient jdbc;

    private UUID conversationId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        UUID projectId = insertZipProject(jdbc, insertWorkspace(jdbc));
        conversationId = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO conversations (id, project_id, title, created_at, updated_at)
                        VALUES (:id, :projectId, 'T', now(), now())
                        """)
                .param("id", conversationId)
                .param("projectId", projectId)
                .update();
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                // status, errorCode, completed, withAnswerMessage, latencyMs, costStatus
                "PENDING,   null,  true,  false, null, UNKNOWN",
                "PENDING,   ERR,   false, false, null, UNKNOWN",
                "PENDING,   null,  false, true,  null, UNKNOWN",
                "PENDING,   null,  false, false, 10,   UNKNOWN",
                "PENDING,   null,  false, false, null, ESTIMATED",
                "FAILED,    null,  true,  false, null, UNKNOWN",
                "FAILED,    ERR,   false, false, null, UNKNOWN",
                "FAILED,    ERR,   true,  true,  null, UNKNOWN",
                "ANSWERED,  null,  true,  false, null, UNKNOWN",
                "PARTIAL,   ERR,   true,  true,  null, UNKNOWN",
                "PARTIAL,   null,  false, true,  null, UNKNOWN",
                "ABSTAINED, ERR,   true,  false, null, UNKNOWN",
                "ABSTAINED, null,  false, false, null, UNKNOWN",
                "UNKNOWN,   null,  true,  false, null, UNKNOWN"
            })
    void answerStateChecksRejectInconsistentRows(
            String status,
            String errorCode,
            boolean completed,
            boolean withAnswerMessage,
            Long latencyMs,
            String costStatus) {
        boolean estimated = costStatus.equals("ESTIMATED");
        assertCheckViolation(
                () ->
                        insertAnswer(
                                status,
                                errorCode,
                                completed,
                                withAnswerMessage ? insertMessage("ASSISTANT") : null,
                                latencyMs,
                                costStatus,
                                estimated ? "0.1" : null,
                                estimated ? "USD" : null,
                                "HYBRID"));
    }

    @Test
    void acceptsAConsistentAnswer() {
        UUID id =
                insertAnswer(
                        "ANSWERED",
                        null,
                        true,
                        insertMessage("ASSISTANT"),
                        10L,
                        "ESTIMATED",
                        "0.000123",
                        "USD",
                        "LEXICAL");

        assertThat(
                        jdbc.sql("SELECT status FROM answers WHERE id = :id")
                                .param("id", id)
                                .query(String.class)
                                .single())
                .isEqualTo("ANSWERED");
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                // costStatus, cost, currency
                "ESTIMATED, null, USD",
                "ESTIMATED, 0.1,  null",
                "UNKNOWN,   0.1,  null",
                "UNKNOWN,   null, USD",
                "MEASURED,  0.1,  USD"
            })
    void answerCostChecksRejectInconsistentRows(String costStatus, String cost, String currency) {
        assertCheckViolation(
                () ->
                        insertAnswer(
                                "FAILED",
                                "ERR",
                                true,
                                null,
                                10L,
                                costStatus,
                                cost,
                                currency,
                                "HYBRID"));
    }

    @Test
    void answerRejectsAnUnknownRetrievalMode() {
        assertCheckViolation(
                () ->
                        insertAnswer(
                                "FAILED", "ERR", true, null, null, "UNKNOWN", null, null, "PATH"));
    }

    @Test
    void messageRejectsAnUnknownRole() {
        assertCheckViolation(() -> insertMessage("SYSTEM_SUMMARY"));
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                // start, end, citedStart, citedEnd, validation
                "0,  5,  null, null, NOT_CITED",
                "5,  4,  null, null, NOT_CITED",
                "10, 20, 9,    15,   CITED",
                "10, 20, 12,   21,   CITED",
                "10, 20, 15,   12,   CITED",
                "10, 20, 12,   null, CITED",
                "10, 20, 12,   15,   NOT_CITED",
                "10, 20, 12,   15,   INVALID_RANGE",
                "10, 20, null, null, IGNORED"
            })
    void evidenceChecksRejectInconsistentRows(
            int start, int end, Integer citedStart, Integer citedEnd, String validation) {
        UUID answerId =
                insertAnswer("FAILED", "ERR", true, null, null, "UNKNOWN", null, null, "HYBRID");
        assertCheckViolation(
                () ->
                        jdbc.sql(
                                        """
                                        INSERT INTO answer_evidence (id, answer_id, snapshot_id,
                                            citation_label, relative_path, content_hash,
                                            start_line, end_line, cited_start_line,
                                            cited_end_line, snippet, final_rank,
                                            validation_status)
                                        VALUES (:id, :answerId, :snapshotId, 'E1', 'a.txt', 'h',
                                            :start, :end, :citedStart, :citedEnd, 's', 1,
                                            :validation)
                                        """)
                                .param("id", UUID.randomUUID())
                                .param("answerId", answerId)
                                .param("snapshotId", UUID.randomUUID())
                                .param("start", start)
                                .param("end", end)
                                .param("citedStart", citedStart)
                                .param("citedEnd", citedEnd)
                                .param("validation", validation)
                                .update());
    }

    @Test
    void evidenceLabelsAreUniquePerAnswer() {
        UUID answerId =
                insertAnswer("FAILED", "ERR", true, null, null, "UNKNOWN", null, null, "HYBRID");
        insertEvidence(answerId, "E1");

        assertThatThrownBy(() -> insertEvidence(answerId, "E1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("answer_evidence_label_key");
    }

    @Test
    void aQuestionHasAtMostOneAnswer() {
        UUID questionId = insertMessage("USER");
        insertAnswer(
                questionId, "PENDING", null, false, null, null, "UNKNOWN", null, null, "HYBRID");

        assertThatThrownBy(
                        () ->
                                insertAnswer(
                                        questionId,
                                        "PENDING",
                                        null,
                                        false,
                                        null,
                                        null,
                                        "UNKNOWN",
                                        null,
                                        null,
                                        "HYBRID"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("answers_question_message_id_key");
    }

    private UUID insertAnswer(
            String status,
            String errorCode,
            boolean completed,
            UUID answerMessageId,
            Long latencyMs,
            String costStatus,
            String cost,
            String currency,
            String retrievalMode) {
        return insertAnswer(
                insertMessage("USER"),
                status,
                errorCode,
                completed,
                answerMessageId,
                latencyMs,
                costStatus,
                cost,
                currency,
                retrievalMode);
    }

    private UUID insertAnswer(
            UUID questionMessageId,
            String status,
            String errorCode,
            boolean completed,
            UUID answerMessageId,
            Long latencyMs,
            String costStatus,
            String cost,
            String currency,
            String retrievalMode) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO answers (id, question_message_id, answer_message_id,
                            snapshot_id, status, retrieval_mode, prompt_version, error_code,
                            latency_ms, estimated_cost, cost_currency, cost_status, created_at,
                            completed_at)
                        VALUES (:id, :questionMessageId, :answerMessageId, :snapshotId, :status,
                            :retrievalMode, 'prompt-v1', :errorCode, :latencyMs,
                            CAST(:cost AS numeric), :currency, :costStatus, now(), :completedAt)
                        """)
                .param("id", id)
                .param("questionMessageId", questionMessageId)
                .param("answerMessageId", answerMessageId)
                .param("snapshotId", UUID.randomUUID())
                .param("status", status)
                .param("retrievalMode", retrievalMode)
                .param("errorCode", errorCode)
                .param("latencyMs", latencyMs)
                .param("cost", cost)
                .param("currency", currency)
                .param("costStatus", costStatus)
                .param("completedAt", completed ? OffsetDateTime.now() : null)
                .update();
        return id;
    }

    private UUID insertMessage(String role) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO messages (id, conversation_id, role, content, created_at)
                        VALUES (:id, :conversationId, :role, 'm', now())
                        """)
                .param("id", id)
                .param("conversationId", conversationId)
                .param("role", role)
                .update();
        return id;
    }

    private void insertEvidence(UUID answerId, String label) {
        jdbc.sql(
                        """
                        INSERT INTO answer_evidence (id, answer_id, snapshot_id, citation_label,
                            relative_path, content_hash, start_line, end_line, snippet,
                            final_rank, validation_status)
                        VALUES (:id, :answerId, :snapshotId, :label, 'a.txt', 'h', 1, 1, 's', 1,
                            'NOT_CITED')
                        """)
                .param("id", UUID.randomUUID())
                .param("answerId", answerId)
                .param("snapshotId", UUID.randomUUID())
                .param("label", label)
                .update();
    }

    private static void assertCheckViolation(Runnable insert) {
        assertThatThrownBy(insert::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("violates check constraint");
    }
}
