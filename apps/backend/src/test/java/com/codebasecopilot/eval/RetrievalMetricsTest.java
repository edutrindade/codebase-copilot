package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.codebasecopilot.eval.QuestionSet.Category;
import com.codebasecopilot.eval.QuestionSet.Difficulty;
import com.codebasecopilot.eval.QuestionSet.ExpectedRange;
import com.codebasecopilot.eval.QuestionSet.Question;
import com.codebasecopilot.eval.RetrievalMetrics.QuestionMetrics;
import com.codebasecopilot.eval.RetrievalMetrics.RetrievedChunk;
import com.codebasecopilot.eval.RetrievalMetrics.Summary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RetrievalMetricsTest {

    private static final ExpectedRange RANGE = range("src/A.java", 10, 20);

    @ParameterizedTest
    @CsvSource({
        "1, 9, false",
        "1, 10, true",
        "12, 18, true",
        "20, 30, true",
        "21, 30, false",
        "1, 100, true"
    })
    void chunkIsRelevantWhenItsLinesOverlapTheRange(int start, int end, boolean relevant) {
        assertThat(RetrievalMetrics.relevant(chunk("src/A.java", start, end), RANGE))
                .isEqualTo(relevant);
    }

    @Test
    void chunkOfAnotherFileIsNotRelevant() {
        assertThat(RetrievalMetrics.relevant(chunk("src/B.java", 10, 20), RANGE)).isFalse();
    }

    @Test
    void otherPathsCountAsTheSameFile() {
        ExpectedRange shared = new ExpectedRange("web/fmt.ts", 1, 8, List.of("admin/fmt.ts"));

        assertThat(RetrievalMetrics.relevant(chunk("admin/fmt.ts", 1, 8), shared)).isTrue();
        assertThat(
                        RetrievalMetrics.relevant(
                                new RetrievedChunk(List.of("lib/fmt.ts", "web/fmt.ts"), 1, 8),
                                shared))
                .isTrue();
        assertThat(RetrievalMetrics.relevant(chunk("lib/fmt.ts", 1, 8), shared)).isFalse();
    }

    @Test
    void recallCountsExpectedRangesFoundWithinK() {
        Question question =
                question(
                        Category.CONCEPTUAL,
                        range("a.py", 1, 10),
                        range("b.py", 1, 10),
                        range("c.py", 1, 10),
                        range("d.py", 1, 10));
        List<RetrievedChunk> ranking = noise(20);
        ranking.set(0, chunk("a.py", 1, 5));
        ranking.set(6, chunk("b.py", 5, 9));
        ranking.set(14, chunk("c.py", 1, 60));

        QuestionMetrics metrics = RetrievalMetrics.of(question, ranking);

        assertThat(metrics.recall()).isEqualTo(Map.of(5, 0.25, 10, 0.5, 20, 0.75));
    }

    @Test
    void twoChunksOfOneRangeCountOnceForRecallAndTwiceForPrecision() {
        Question question = question(Category.CONCEPTUAL, RANGE, range("src/B.java", 1, 5));
        List<RetrievedChunk> ranking = noise(10);
        ranking.set(1, chunk("src/A.java", 1, 12));
        ranking.set(2, chunk("src/A.java", 15, 40));

        QuestionMetrics metrics = RetrievalMetrics.of(question, ranking);

        assertThat(metrics.recall().get(10)).isEqualTo(0.5);
        assertThat(metrics.precision()).isEqualTo(0.2);
    }

    @Test
    void reciprocalRankOfTheFirstRelevantChunk() {
        List<RetrievedChunk> ranking = noise(20);
        ranking.set(3, chunk("src/A.java", 15, 16));
        ranking.set(7, chunk("src/A.java", 10, 11));

        QuestionMetrics metrics =
                RetrievalMetrics.of(question(Category.CONCEPTUAL, RANGE), ranking);

        assertThat(metrics.mrr()).isEqualTo(0.25);
        assertThat(metrics.firstRelevant()).isEqualTo(4);
    }

    @Test
    void reciprocalRankIsZeroBeyondTen() {
        List<RetrievedChunk> ranking = noise(20);
        ranking.set(10, chunk("src/A.java", 10, 11));

        QuestionMetrics metrics =
                RetrievalMetrics.of(question(Category.CONCEPTUAL, RANGE), ranking);

        assertThat(metrics.mrr()).isZero();
        assertThat(metrics.firstRelevant()).isEqualTo(11);
        assertThat(metrics.recall()).isEqualTo(Map.of(5, 0.0, 10, 0.0, 20, 1.0));
    }

    @Test
    void precisionDividesByTenEvenWithFewerResults() {
        List<RetrievedChunk> ranking = List.of(chunk("src/A.java", 10, 11));

        QuestionMetrics metrics =
                RetrievalMetrics.of(question(Category.CONCEPTUAL, RANGE), ranking);

        assertThat(metrics.precision()).isEqualTo(0.1);
        assertThat(metrics.mrr()).isEqualTo(1.0);
    }

    @Test
    void emptyRankingScoresZero() {
        QuestionMetrics metrics =
                RetrievalMetrics.of(question(Category.CONCEPTUAL, RANGE), List.of());

        assertThat(metrics.recall()).isEqualTo(Map.of(5, 0.0, 10, 0.0, 20, 0.0));
        assertThat(metrics.mrr()).isZero();
        assertThat(metrics.precision()).isZero();
        assertThat(metrics.firstRelevant()).isNull();
    }

    @Test
    void fileCoverageCountsExpectedFilesHitInTheTopTen() {
        Question question =
                question(
                        Category.CROSS_FILE_FLOW,
                        range("a.py", 1, 10),
                        range("a.py", 50, 60),
                        range("b.py", 1, 10),
                        new ExpectedRange("c.py", 1, 10, List.of("c2.py")));
        List<RetrievedChunk> ranking = noise(20);
        // Any chunk of the file counts, even outside the expected lines.
        ranking.set(0, chunk("a.py", 200, 210));
        ranking.set(9, chunk("c2.py", 1, 2));
        ranking.set(10, chunk("b.py", 1, 10));

        QuestionMetrics metrics = RetrievalMetrics.of(question, ranking);

        assertThat(metrics.fileCoverage()).isCloseTo(2.0 / 3, within(1e-9));
    }

    @Test
    void fileCoverageOnlyForCrossFileFlowQuestions() {
        QuestionMetrics metrics =
                RetrievalMetrics.of(
                        question(Category.EXACT_IDENTIFIER, RANGE),
                        List.of(chunk("src/A.java", 10, 11)));

        assertThat(metrics.fileCoverage()).isNull();
    }

    @Test
    void questionsThatMustAbstainHaveNoMetrics() {
        Question question =
                new Question(
                        "q",
                        "?",
                        "en",
                        Category.UNANSWERABLE,
                        Difficulty.EASY,
                        List.of(),
                        true,
                        List.of(),
                        null);

        assertThatThrownBy(() -> RetrievalMetrics.of(question, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void summaryAveragesQuestionsAndCoverageOnlyWhereMeasured() {
        QuestionMetrics first =
                new QuestionMetrics(Map.of(5, 1.0, 10, 1.0, 20, 1.0), 1.0, 0.3, 0.5, 1);
        QuestionMetrics second =
                new QuestionMetrics(Map.of(5, 0.0, 10, 0.5, 20, 1.0), 0.5, 0.1, null, 2);

        Summary summary = RetrievalMetrics.summarize(List.of(first, second));

        assertThat(summary.questions()).isEqualTo(2);
        assertThat(summary.recall()).isEqualTo(Map.of(5, 0.5, 10, 0.75, 20, 1.0));
        assertThat(summary.mrr()).isEqualTo(0.75);
        assertThat(summary.precision()).isCloseTo(0.2, within(1e-9));
        assertThat(summary.fileCoverage()).isEqualTo(0.5);
        assertThat(summary.coverageQuestions()).isEqualTo(1);
    }

    @Test
    void summaryOfNothingIsZeroWithoutCoverage() {
        Summary summary = RetrievalMetrics.summarize(List.of());

        assertThat(summary.questions()).isZero();
        assertThat(summary.mrr()).isZero();
        assertThat(summary.fileCoverage()).isNull();
    }

    private static Question question(Category category, ExpectedRange... expected) {
        return new Question(
                "q",
                "?",
                "en",
                category,
                Difficulty.EASY,
                List.of(expected),
                false,
                List.of(),
                null);
    }

    private static ExpectedRange range(String path, int start, int end) {
        return new ExpectedRange(path, start, end, List.of());
    }

    private static RetrievedChunk chunk(String path, int start, int end) {
        return new RetrievedChunk(List.of(path), start, end);
    }

    private static List<RetrievedChunk> noise(int size) {
        List<RetrievedChunk> ranking = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ranking.add(chunk("noise/N" + i + ".java", 1, 60));
        }
        return ranking;
    }
}
