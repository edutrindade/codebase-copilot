package com.codebasecopilot.history;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static com.codebasecopilot.IndexRows.count;
import static com.codebasecopilot.IndexRows.insertContent;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class AnswerHistoryIT {

    @Autowired private AnswerHistory history;
    @Autowired private JdbcClient jdbc;

    private UUID projectId;
    private UUID snapshotId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        projectId = insertZipProject(jdbc, insertWorkspace(jdbc));
        snapshotId = insertSnapshot(jdbc, projectId, "READY");
    }

    @Test
    void startsAConversationWithAPendingAnswer() {
        PendingAnswer pending = history.start(question(null, "  Como o\nreembolso é calculado?  "));

        StoredAnswer answer = history.findAnswer(pending.answerId()).orElseThrow();
        assertThat(answer.projectId()).isEqualTo(projectId);
        assertThat(answer.conversationId()).isEqualTo(pending.conversationId());
        assertThat(answer.question()).isEqualTo("  Como o\nreembolso é calculado?  ");
        assertThat(answer.snapshotId()).isEqualTo(snapshotId);
        assertThat(answer.status()).isEqualTo(AnswerStatus.PENDING);
        assertThat(answer.text()).isNull();
        assertThat(answer.retrievalMode()).isEqualTo("HYBRID");
        assertThat(answer.promptVersion()).isEqualTo("prompt-v1");
        assertThat(answer.metrics()).isEqualTo(AnswerMetrics.none());
        assertThat(answer.completedAt()).isNull();
        assertThat(answer.evidence()).isEmpty();
        assertThat(title(pending.conversationId())).isEqualTo("Como o reembolso é calculado?");
    }

    @Test
    void addsQuestionsToAnExistingConversation() {
        PendingAnswer first = history.start(question(null, "Primeira"));

        PendingAnswer second = history.start(question(first.conversationId(), "Segunda"));

        assertThat(second.conversationId()).isEqualTo(first.conversationId());
        assertThat(count(jdbc, "conversations")).isEqualTo(1);
        assertThat(title(first.conversationId())).isEqualTo("Primeira");
        assertThat(
                        jdbc.sql("SELECT content FROM messages ORDER BY created_at, id")
                                .query(String.class)
                                .list())
                .containsExactly("Primeira", "Segunda");
    }

    @Test
    void rejectsAConversationOfAnotherProject() {
        UUID otherProject = insertZipProject(jdbc, insertWorkspace(jdbc));
        PendingAnswer other =
                history.start(
                        new NewQuestion(
                                otherProject, null, "Outra", snapshotId, "HYBRID", "prompt-v1"));

        assertThatThrownBy(() -> history.start(question(other.conversationId(), "Pergunta")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        assertThatThrownBy(() -> history.start(question(UUID.randomUUID(), "Pergunta")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        assertThat(count(jdbc, "messages")).isEqualTo(1);
    }

    @Test
    void rejectsAMissingProjectWithoutPersistingAnything() {
        assertThatThrownBy(
                        () ->
                                history.start(
                                        new NewQuestion(
                                                UUID.randomUUID(),
                                                null,
                                                "Pergunta",
                                                snapshotId,
                                                "HYBRID",
                                                "prompt-v1")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
        assertThat(count(jdbc, "conversations")).isZero();
    }

    @Test
    void completesAnAnswerWithItsEvidence() {
        PendingAnswer pending = history.start(question(null, "Pergunta"));
        UUID chunkId = UUID.randomUUID();
        AnswerEvidence cited =
                new AnswerEvidence(
                        chunkId,
                        "E1",
                        "src/Refund.java",
                        List.of("copy/Refund.java"),
                        "hash-1",
                        12,
                        58,
                        20,
                        31,
                        "class Refund {}",
                        2,
                        1,
                        null,
                        1,
                        EvidenceValidation.CITED);
        AnswerEvidence ignored =
                new AnswerEvidence(
                        null,
                        "E2",
                        "src/Other.java",
                        List.of(),
                        "hash-2",
                        1,
                        10,
                        null,
                        null,
                        "class Other {}",
                        null,
                        3,
                        null,
                        2,
                        EvidenceValidation.NOT_CITED);

        boolean completed =
                history.complete(
                        pending.answerId(),
                        new AnswerOutcome(
                                AnswerStatus.ANSWERED,
                                "Calculado em Refund [E1].",
                                null,
                                List.of("INVALID_CITATIONS_REMOVED"),
                                1,
                                "fake",
                                "fake-chat",
                                Map.of("candidates", Map.of("lexical", 20, "vector", 20)),
                                new AnswerMetrics(
                                        1240L,
                                        5120,
                                        240,
                                        new BigDecimal("0.001200"),
                                        "USD",
                                        CostStatus.ESTIMATED),
                                List.of(ignored, cited)));

        assertThat(completed).isTrue();
        StoredAnswer answer = history.findAnswer(pending.answerId()).orElseThrow();
        assertThat(answer.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(answer.text()).isEqualTo("Calculado em Refund [E1].");
        assertThat(answer.errorCode()).isNull();
        assertThat(answer.warnings()).containsExactly("INVALID_CITATIONS_REMOVED");
        assertThat(answer.invalidCitationCount()).isEqualTo(1);
        assertThat(answer.generationProvider()).isEqualTo("fake");
        assertThat(answer.generationModel()).isEqualTo("fake-chat");
        assertThat(answer.retrievalMetadata())
                .isEqualTo(Map.of("candidates", Map.of("lexical", 20, "vector", 20)));
        assertThat(answer.metrics())
                .isEqualTo(
                        new AnswerMetrics(
                                1240L,
                                5120,
                                240,
                                new BigDecimal("0.001200"),
                                "USD",
                                CostStatus.ESTIMATED));
        assertThat(answer.completedAt()).isNotNull();
        assertThat(answer.evidence())
                .extracting(StoredEvidence::evidence)
                .containsExactly(cited, ignored);
        assertThat(answer.evidence())
                .extracting(StoredEvidence::snapshotId)
                .containsOnly(snapshotId);
        assertThat(
                        jdbc.sql("SELECT role FROM messages ORDER BY created_at, role DESC")
                                .query(String.class)
                                .list())
                .containsExactly("USER", "ASSISTANT");
    }

    @Test
    void failsAnAnswerWithoutAnAssistantMessage() {
        PendingAnswer pending = history.start(question(null, "Pergunta"));

        history.complete(pending.answerId(), failed("GENERATION_PROVIDER_UNAVAILABLE"));

        StoredAnswer answer = history.findAnswer(pending.answerId()).orElseThrow();
        assertThat(answer.status()).isEqualTo(AnswerStatus.FAILED);
        assertThat(answer.errorCode()).isEqualTo("GENERATION_PROVIDER_UNAVAILABLE");
        assertThat(answer.text()).isNull();
        assertThat(answer.question()).isEqualTo("Pergunta");
        assertThat(count(jdbc, "messages")).isEqualTo(1);
    }

    @Test
    void abstainsWithoutTextOrEvidence() {
        PendingAnswer pending = history.start(question(null, "Pergunta"));

        history.complete(
                pending.answerId(),
                new AnswerOutcome(
                        AnswerStatus.ABSTAINED,
                        null,
                        null,
                        List.of(),
                        0,
                        null,
                        null,
                        null,
                        AnswerMetrics.none(),
                        List.of()));

        StoredAnswer answer = history.findAnswer(pending.answerId()).orElseThrow();
        assertThat(answer.status()).isEqualTo(AnswerStatus.ABSTAINED);
        assertThat(answer.retrievalMetadata()).isNull();
    }

    @Test
    void completesOnlyOnce() {
        PendingAnswer pending = history.start(question(null, "Pergunta"));
        history.complete(pending.answerId(), failed("GENERATION_RATE_LIMITED"));

        boolean again = history.complete(pending.answerId(), failed("GENERATION_REFUSED"));

        assertThat(again).isFalse();
        assertThat(history.findAnswer(pending.answerId()).orElseThrow().errorCode())
                .isEqualTo("GENERATION_RATE_LIMITED");
    }

    @Test
    void ignoresTheCompletionOfAnAnswerRemovedWithItsProject() {
        PendingAnswer pending = history.start(question(null, "Pergunta"));
        jdbc.sql("DELETE FROM projects WHERE id = :id").param("id", projectId).update();

        assertThat(history.complete(pending.answerId(), failed("GENERATION_REFUSED"))).isFalse();
        assertThat(history.findAnswer(pending.answerId())).isEmpty();
    }

    @Test
    void removingTheProjectRemovesItsHistory() {
        UUID otherProject = insertZipProject(jdbc, insertWorkspace(jdbc));
        PendingAnswer kept =
                history.start(
                        new NewQuestion(
                                otherProject, null, "Outra", snapshotId, "HYBRID", "prompt-v1"));
        completeWithEvidence(history.start(question(null, "Pergunta")));
        completeWithEvidence(kept);

        jdbc.sql("DELETE FROM projects WHERE id = :id").param("id", projectId).update();

        assertThat(count(jdbc, "conversations")).isEqualTo(1);
        assertThat(count(jdbc, "messages")).isEqualTo(2);
        assertThat(count(jdbc, "answers")).isEqualTo(1);
        assertThat(count(jdbc, "answer_evidence")).isEqualTo(1);
        assertThat(history.findAnswer(kept.answerId())).isPresent();
    }

    @Test
    void evidenceOutlivesSnapshotPruningAndChunkCleanup() {
        insertContent(jdbc, projectId, "hash-1", "class Refund {}");
        UUID chunkId = insertChunk("hash-1");
        PendingAnswer pending = history.start(question(null, "Pergunta"));
        completeWithEvidence(pending, chunkId);

        jdbc.sql("UPDATE snapshots SET pruned_at = now() WHERE id = :id")
                .param("id", snapshotId)
                .update();
        jdbc.sql("DELETE FROM chunks").update();
        jdbc.sql("DELETE FROM file_contents").update();
        jdbc.sql("DELETE FROM snapshots").update();

        StoredAnswer answer = history.findAnswer(pending.answerId()).orElseThrow();
        assertThat(answer.snapshotId()).isEqualTo(snapshotId);
        assertThat(answer.evidence())
                .singleElement()
                .satisfies(
                        stored -> {
                            assertThat(stored.snapshotId()).isEqualTo(snapshotId);
                            assertThat(stored.evidence().chunkId()).isEqualTo(chunkId);
                            assertThat(stored.evidence().snippet()).isEqualTo("class Refund {}");
                        });
    }

    private NewQuestion question(UUID conversationId, String text) {
        return new NewQuestion(projectId, conversationId, text, snapshotId, "HYBRID", "prompt-v1");
    }

    private static AnswerOutcome failed(String code) {
        return new AnswerOutcome(
                AnswerStatus.FAILED,
                null,
                code,
                List.of(),
                0,
                "fake",
                "fake-chat",
                null,
                AnswerMetrics.none(),
                List.of());
    }

    private void completeWithEvidence(PendingAnswer pending) {
        completeWithEvidence(pending, null);
    }

    private void completeWithEvidence(PendingAnswer pending, UUID chunkId) {
        history.complete(
                pending.answerId(),
                new AnswerOutcome(
                        AnswerStatus.PARTIAL,
                        "Resposta [E1].",
                        null,
                        List.of(),
                        0,
                        "fake",
                        "fake-chat",
                        null,
                        AnswerMetrics.none(),
                        List.of(
                                new AnswerEvidence(
                                        chunkId,
                                        "E1",
                                        "src/Refund.java",
                                        List.of(),
                                        "hash-1",
                                        1,
                                        1,
                                        null,
                                        null,
                                        "class Refund {}",
                                        1,
                                        null,
                                        null,
                                        1,
                                        EvidenceValidation.CITED))));
    }

    private UUID insertChunk(String contentHash) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO chunks (id, project_id, content_hash, chunking_version,
                                            ordinal, start_line, end_line, content, chunk_hash,
                                            token_count_estimate)
                        VALUES (:id, :projectId, :hash, 'lines-v1', 0, 1, 1, 'class Refund {}',
                                'chunk-hash', 4)
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("hash", contentHash)
                .update();
        return id;
    }

    private String title(UUID conversationId) {
        return jdbc.sql("SELECT title FROM conversations WHERE id = :id")
                .param("id", conversationId)
                .query(String.class)
                .single();
    }
}
