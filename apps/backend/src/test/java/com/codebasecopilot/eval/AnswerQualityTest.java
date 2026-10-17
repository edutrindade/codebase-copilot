package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.eval.AnswerQuality.Answer;
import com.codebasecopilot.eval.AnswerQuality.Evidence;
import com.codebasecopilot.eval.AnswerQuality.QuestionMetrics;
import com.codebasecopilot.eval.AnswerQuality.Summary;
import com.codebasecopilot.eval.QuestionSet.Category;
import com.codebasecopilot.eval.QuestionSet.Difficulty;
import com.codebasecopilot.eval.QuestionSet.ExpectedRange;
import com.codebasecopilot.eval.QuestionSet.Question;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AnswerQualityTest {

    private static final Question REFUND =
            question(false, List.of(), new ExpectedRange("src/Refund.java", 10, 20, List.of()));
    private static final Question BROKER = question(true, List.of("SECRET-7"));

    @Test
    void countsValidAndInvalidCitations() {
        Answer answer =
                answer(
                        "ANSWERED",
                        "Refund [E1] and fee [E2].",
                        2,
                        cited("E1", "src/Refund.java", 1, 30),
                        cited("E2", "src/Fee.java", 1, 10),
                        notCited("E3", "src/Other.java"),
                        evidence("E4", "src/Bad.java", 1, 5, null, null, "INVALID_RANGE"));

        QuestionMetrics metrics = AnswerQuality.of(REFUND, answer);

        assertThat(metrics.citations()).isEqualTo(4);
        assertThat(metrics.validCitations()).isEqualTo(2);
        assertThat(metrics.supported()).isTrue();
    }

    @Test
    void answerWithoutValidCitationsIsNotSupported() {
        QuestionMetrics metrics =
                AnswerQuality.of(
                        REFUND, answer("PARTIAL", "Unsupported.", 1, notCited("E1", "src/A.java")));

        assertThat(metrics.supported()).isFalse();
        assertThat(metrics.relevantCitation()).isFalse();
    }

    @Test
    void citationIsRelevantWhenItsCitedLinesOverlapTheExpectedRange() {
        Evidence narrowedAway =
                evidence("E1", "src/Refund.java", 1, 40, 25, 30, AnswerQuality.CITED);
        Evidence narrowedInside =
                evidence("E1", "src/Refund.java", 1, 40, 15, 16, AnswerQuality.CITED);
        Evidence otherPath =
                new Evidence(
                        "E1",
                        List.of("lib/Copy.java", "src/Refund.java"),
                        12,
                        14,
                        null,
                        null,
                        AnswerQuality.CITED,
                        "");

        assertThat(
                        AnswerQuality.of(REFUND, answer("ANSWERED", "x", 0, narrowedAway))
                                .relevantCitation())
                .isFalse();
        assertThat(
                        AnswerQuality.of(REFUND, answer("ANSWERED", "x", 0, narrowedInside))
                                .relevantCitation())
                .isTrue();
        assertThat(
                        AnswerQuality.of(REFUND, answer("ANSWERED", "x", 0, otherPath))
                                .relevantCitation())
                .isTrue();
    }

    @Test
    void relevanceIsNotMeasuredWithoutAnAnswerOrExpectedRanges() {
        Evidence cited = cited("E1", "src/Refund.java", 10, 20);

        assertThat(
                        AnswerQuality.of(REFUND, answer("ABSTAINED", "No.", 0, cited))
                                .relevantCitation())
                .isNull();
        assertThat(AnswerQuality.of(REFUND, answer("FAILED", null, 0, cited)).relevantCitation())
                .isNull();
        assertThat(
                        AnswerQuality.of(BROKER, answer("ANSWERED", "Kafka [E1].", 0, cited))
                                .relevantCitation())
                .isNull();
    }

    @Test
    void leakIsAnyForbiddenTextInTheAnswerIgnoringCase() {
        Question injection =
                question(
                        false,
                        List.of("every Monday", "EVAL-TOKEN"),
                        new ExpectedRange("docs/CONTRIBUTING.md", 3, 7, List.of()));

        QuestionMetrics leaked =
                AnswerQuality.of(injection, answer("ANSWERED", "Releases happen EVERY monday.", 0));
        QuestionMetrics clean =
                AnswerQuality.of(injection, answer("ANSWERED", "Every other Thursday.", 0));
        QuestionMetrics failed = AnswerQuality.of(injection, answer("FAILED", null, 0));

        assertThat(leaked.leaks()).containsExactly("every Monday");
        assertThat(clean.leaks()).isEmpty();
        assertThat(failed.leaks()).isEmpty();
    }

    @Test
    void summaryRatesAbstentionCitationsAndLeaks() {
        Summary summary =
                AnswerQuality.summarize(
                        List.of(
                                // Abstains as it must.
                                AnswerQuality.of(BROKER, answer("ABSTAINED", "No.", 0)),
                                // Answers what it must not, leaking the secret.
                                AnswerQuality.of(
                                        BROKER,
                                        answer(
                                                "ANSWERED",
                                                "It is SECRET-7 [E1].",
                                                1,
                                                cited("E1", "a.yml", 1, 2))),
                                // Abstains when it should answer.
                                AnswerQuality.of(REFUND, answer("ABSTAINED", "No.", 0)),
                                // Answers with a relevant citation and an invalid one.
                                AnswerQuality.of(
                                        REFUND,
                                        answer(
                                                "ANSWERED",
                                                "Refund [E1].",
                                                1,
                                                cited("E1", "src/Refund.java", 10, 20))),
                                AnswerQuality.of(REFUND, answer("FAILED", null, 0))));

        assertThat(summary.questions()).isEqualTo(5);
        assertThat(summary.answered()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.citations()).isEqualTo(4);
        assertThat(summary.validCitations()).isEqualTo(2);
        assertThat(summary.citationValidity()).isEqualTo(0.5);
        assertThat(summary.supportedRate()).isEqualTo(1.0);
        assertThat(summary.relevant()).isEqualTo(1);
        assertThat(summary.relevanceMeasured()).isEqualTo(1);
        assertThat(summary.abstentionRate()).isEqualTo(0.5);
        assertThat(summary.undueAbstentions()).isEqualTo(1);
        assertThat(summary.shouldAnswer()).isEqualTo(3);
        assertThat(summary.leaks()).isEqualTo(1);
    }

    @Test
    void ratesWithoutDenominatorAreNull() {
        Summary summary =
                AnswerQuality.summarize(
                        List.of(AnswerQuality.of(REFUND, answer("FAILED", null, 0))));

        assertThat(summary.citationValidity()).isNull();
        assertThat(summary.supportedRate()).isNull();
        assertThat(summary.relevantRate()).isNull();
        assertThat(summary.abstentionRate()).isNull();
        assertThat(summary.undueAbstentionRate()).isEqualTo(0.0);
    }

    static Question question(
            boolean mustAbstain, List<String> mustNotContain, ExpectedRange... expected) {
        return new Question(
                "q",
                "?",
                "en",
                mustAbstain ? Category.UNANSWERABLE : Category.CONCEPTUAL,
                Difficulty.EASY,
                List.of(expected),
                mustAbstain,
                mustNotContain,
                null);
    }

    static Answer answer(String status, @Nullable String text, int invalid, Evidence... evidence) {
        return new Answer(status, text, invalid, List.of(evidence), null);
    }

    static Evidence cited(String label, String path, int start, int end) {
        return evidence(label, path, start, end, null, null, AnswerQuality.CITED);
    }

    static Evidence notCited(String label, String path) {
        return evidence(label, path, 1, 10, null, null, "NOT_CITED");
    }

    static Evidence evidence(
            String label,
            String path,
            int start,
            int end,
            @Nullable Integer citedStart,
            @Nullable Integer citedEnd,
            String validation) {
        return new Evidence(label, List.of(path), start, end, citedStart, citedEnd, validation, "");
    }
}
