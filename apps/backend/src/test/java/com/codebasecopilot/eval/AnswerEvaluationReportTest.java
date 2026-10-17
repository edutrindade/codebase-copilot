package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.eval.AnswerEvaluationReport.CorpusRun;
import com.codebasecopilot.eval.AnswerEvaluationReport.QuestionRun;
import com.codebasecopilot.eval.AnswerEvaluationReport.Run;
import com.codebasecopilot.eval.AnswerEvaluationReport.Usage;
import com.codebasecopilot.eval.AnswerQuality.Answer;
import com.codebasecopilot.eval.AnswerQuality.Evidence;
import com.codebasecopilot.eval.QuestionSet.Category;
import com.codebasecopilot.eval.QuestionSet.Corpus;
import com.codebasecopilot.eval.QuestionSet.Difficulty;
import com.codebasecopilot.eval.QuestionSet.ExpectedRange;
import com.codebasecopilot.eval.QuestionSet.Question;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnswerEvaluationReportTest {

    private static final String SNIPPET =
            "class Refund {\n  long amount() {\n    return total - fee;\n  }\n}\n";

    @TempDir Path dir;

    @Test
    void writesJsonAndMarkdownNamedByDateAndConfig() throws IOException {
        Path json = AnswerEvaluationReport.write(dir, run());

        assertThat(json).hasFileName("2026-10-16-answers-fake-m.json");
        assertThat(dir.resolve("2026-10-16-answers-fake-m.md")).exists();
        DocumentContext doc = JsonPath.parse(Files.readString(json));
        assertThat((Integer) doc.read("$.metrics.all.questions")).isEqualTo(3);
        assertThat((Double) doc.read("$.metrics.all.citationValidity")).isEqualTo(0.6667);
        assertThat((Double) doc.read("$.metrics.all.abstentionRate")).isEqualTo(1.0);
        assertThat((Integer) doc.read("$.metrics.all.leaks")).isEqualTo(1);
        assertThat((Integer) doc.read("$.metrics.category.UNANSWERABLE.questions")).isEqualTo(1);
        assertThat((Double) doc.read("$.metrics.language.en.relevantRate")).isEqualTo(1.0);
        assertThat((Integer) doc.read("$.metrics.all.usage.inputTokens.p95")).isEqualTo(900);
        assertThat((Double) doc.read("$.metrics.all.usage.estimatedCostUsd.total"))
                .isEqualTo(0.003);
        // The cached answer stays out of the latency.
        assertThat((Integer) doc.read("$.metrics.all.usage.latencyMs.answers")).isEqualTo(2);
        assertThat((Integer) doc.read("$.metrics.all.usage.cachedAnswers")).isEqualTo(1);
        assertThat((String) doc.read("$.corpora[0].questions[0].evidence[0].citedLines"))
                .isEqualTo("2-3");
        assertThat((List<String>) doc.read("$.corpora[0].questions[2].metrics.leaks"))
                .containsExactly("every Monday");
    }

    @Test
    void jsonHasReferencesButNoSnippets() throws IOException {
        Path json = AnswerEvaluationReport.write(dir, run());

        assertThat(Files.readString(json)).doesNotContain("return total - fee");
    }

    @Test
    void markdownSummarizesAndShowsEachAnswerWithItsCitedLines() {
        String markdown = AnswerEvaluationReport.markdown(run());

        assertThat(markdown)
                .contains("| Todas | 3 | 2 | 0 | 0.667 (2/3) | 1.000 (2/2) | 1.000 (2/2) |")
                .contains("| Corpus fixture | 3 |")
                .contains("#### fx-01 · CONCEPTUAL · en")
                .contains("**Esperado:** `src/Refund.java:2-3`")
                .contains("> Refund is total minus fee [E1].")
                .contains("`E1` `src/Refund.java:2-3`\n\n```\n2|   long amount() {\n3|     return")
                .doesNotContain("1| class Refund")
                .contains("**Esperado:** abstenção")
                .contains("**Vazamento:** `every Monday`")
                .contains("- [ ] Correta\n- [ ] Sustentada pelas citações\n- Nota:");
    }

    @Test
    void fenceIsLongerThanAnyBackticksInTheSnippet() {
        assertThat(AnswerEvaluationReport.fence("plain")).isEqualTo("```");
        assertThat(AnswerEvaluationReport.fence("a ```` b ` c")).isEqualTo("`````");
    }

    @Test
    void citedLinesOfAWholeEvidence() {
        Evidence whole =
                new Evidence(
                        "E1", List.of("a.txt"), 7, 8, null, null, AnswerQuality.CITED, "x\ny\n");

        assertThat(AnswerEvaluationReport.citedLines(whole)).isEqualTo("7| x\n8| y");
    }

    // Three fixture questions: answered with one valid and one invalid citation (cached), a
    // correct abstention, and an answer that repeats the injected text.
    private static Run run() {
        Question refund =
                question(
                        "fx-01",
                        Category.CONCEPTUAL,
                        false,
                        List.of(),
                        new ExpectedRange("src/Refund.java", 2, 3, List.of()));
        Question broker = question("fx-10", Category.UNANSWERABLE, true, List.of());
        Question injection =
                question(
                        "fx-08",
                        Category.PROMPT_INJECTION,
                        false,
                        List.of("every Monday"),
                        new ExpectedRange("docs/CONTRIBUTING.md", 3, 7, List.of()));
        Evidence refundEvidence =
                new Evidence(
                        "E1", List.of("src/Refund.java"), 1, 5, 2, 3, AnswerQuality.CITED, SNIPPET);
        Evidence contributing =
                new Evidence(
                        "E1",
                        List.of("docs/CONTRIBUTING.md"),
                        1,
                        13,
                        null,
                        null,
                        AnswerQuality.CITED,
                        "# Contributing\n");
        List<QuestionRun> questions =
                List.of(
                        new QuestionRun(
                                refund,
                                new Answer(
                                        "ANSWERED",
                                        "Refund is total minus fee [E1].",
                                        1,
                                        List.of(refundEvidence),
                                        null),
                                new Usage(900, 100, new BigDecimal("0.002"), 5L, 3L, true)),
                        new QuestionRun(
                                broker,
                                new Answer("ABSTAINED", "Sem evidência.", 0, List.of(), null),
                                new Usage(null, null, null, 40L, null, false)),
                        new QuestionRun(
                                injection,
                                new Answer(
                                        "ANSWERED",
                                        "Releases happen every Monday [E1].",
                                        0,
                                        List.of(contributing),
                                        null),
                                new Usage(800, 50, new BigDecimal("0.001"), 900L, 850L, false)));
        CorpusRun corpus =
                new CorpusRun(
                        new Corpus(
                                "fixture",
                                "eval/corpus/fixture",
                                null,
                                null,
                                null,
                                null,
                                "eval/corpus/fixture.sha256"),
                        "0".repeat(64),
                        "lines-v1",
                        3,
                        3,
                        questions);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("generationModel", "m");
        config.put("embeddingModel", null);
        return new Run("2026-10-16", "answers-fake-m", config, List.of(corpus));
    }

    private static Question question(
            String id,
            Category category,
            boolean mustAbstain,
            List<String> mustNotContain,
            ExpectedRange... expected) {
        return new Question(
                id,
                "Question " + id + "?",
                "en",
                category,
                Difficulty.EASY,
                List.of(expected),
                mustAbstain,
                mustNotContain,
                null);
    }
}
