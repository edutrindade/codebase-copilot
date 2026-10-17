package com.codebasecopilot.answering;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.ImportJobRows.insertZipProject;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.history.AnswerHistory;
import com.codebasecopilot.history.AnswerMetrics;
import com.codebasecopilot.history.AnswerOutcome;
import com.codebasecopilot.history.AnswerStatus;
import com.codebasecopilot.history.NewQuestion;
import com.codebasecopilot.history.StoredAnswer;
import com.codebasecopilot.providers.GenerationProperties;
import com.codebasecopilot.providers.ProviderProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class AnswerRecoveryIT {

    @Autowired private AnswerRecovery recovery;
    @Autowired private AnswerHistory history;
    @Autowired private ProviderProperties embeddings;
    @Autowired private GenerationProperties generation;
    @Autowired private JdbcClient jdbc;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        projectId = insertZipProject(jdbc, insertWorkspace(jdbc));
    }

    @Test
    void failsPendingAnswersOlderThanTheLongestRequest() {
        Duration longest = embeddings.timeout().plus(generation.timeout());
        UUID stale = start(longest.plusSeconds(5));
        UUID recent = start(longest.minusSeconds(5));
        UUID finished = start(longest.plusSeconds(5));
        history.complete(
                finished,
                new AnswerOutcome(
                        AnswerStatus.FAILED,
                        null,
                        "GENERATION_REFUSED",
                        List.of(),
                        0,
                        null,
                        null,
                        null,
                        AnswerMetrics.none(),
                        List.of()));

        recovery.recover();

        StoredAnswer interrupted = history.findAnswer(stale).orElseThrow();
        assertThat(interrupted.status()).isEqualTo(AnswerStatus.FAILED);
        assertThat(interrupted.errorCode()).isEqualTo("ANSWER_INTERRUPTED");
        assertThat(interrupted.completedAt()).isNotNull();
        assertThat(interrupted.question()).isEqualTo("Pergunta");
        assertThat(history.findAnswer(recent).orElseThrow().status())
                .isEqualTo(AnswerStatus.PENDING);
        assertThat(history.findAnswer(finished).orElseThrow().errorCode())
                .isEqualTo("GENERATION_REFUSED");
    }

    @Test
    void aRecoveredAnswerIsNotCompletedLater() {
        UUID stale = start(embeddings.timeout().plus(generation.timeout()).plusSeconds(5));
        recovery.recover();

        boolean completed =
                history.complete(
                        stale,
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

        assertThat(completed).isFalse();
        assertThat(history.findAnswer(stale).orElseThrow().errorCode())
                .isEqualTo("ANSWER_INTERRUPTED");
    }

    // Moves the answer back in time, as if its request had started that long ago.
    private UUID start(Duration age) {
        UUID answerId =
                history.start(
                                new NewQuestion(
                                        projectId,
                                        null,
                                        "Pergunta",
                                        UUID.randomUUID(),
                                        "HYBRID",
                                        "prompt-v1"))
                        .answerId();
        jdbc.sql(
                        """
                        UPDATE answers
                        SET created_at = created_at - make_interval(secs => :seconds)
                        WHERE id = :id
                        """)
                .param("id", answerId)
                .param("seconds", age.toSeconds())
                .update();
        return answerId;
    }
}
