package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.eval.EvaluationReport.CorpusRun;
import com.codebasecopilot.eval.EvaluationReport.QuestionRun;
import com.codebasecopilot.eval.EvaluationReport.Run;
import com.codebasecopilot.eval.QuestionSet.Category;
import com.codebasecopilot.eval.QuestionSet.Corpus;
import com.codebasecopilot.eval.QuestionSet.Difficulty;
import com.codebasecopilot.eval.QuestionSet.ExpectedRange;
import com.codebasecopilot.eval.QuestionSet.Question;
import com.codebasecopilot.eval.RetrievalMetrics.RetrievedChunk;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationReportTest {

    @TempDir Path dir;

    @Test
    void writesJsonAndMarkdownNamedByDateAndConfig() throws IOException {
        Path json = EvaluationReport.write(dir, run(List.of(5L, 7L)));

        assertThat(json).hasFileName("2026-10-16-lexical.json");
        assertThat(dir.resolve("2026-10-16-lexical.md")).exists();
        DocumentContext doc = JsonPath.parse(Files.readString(json));
        assertThat((Double) doc.read("$.metrics.LEXICAL.all['recall@10']")).isEqualTo(0.5);
        assertThat((Double) doc.read("$.metrics.LEXICAL_PATH.all['mrr@10']")).isEqualTo(1.0);
        assertThat((Integer) doc.read("$.metrics.LEXICAL.category.CROSS_FILE_FLOW.questions"))
                .isEqualTo(1);
        assertThat((Double) doc.read("$.metrics.LEXICAL.language.pt-BR['mrr@10']")).isEqualTo(0.5);
        assertThat((List<String>) doc.read("$.corpora[0].questions[0].variants.LEXICAL.top"))
                .containsExactly("src/Other.java:1-60", "src/A.java,src/Copy.java:1-20");
        assertThat((Integer) doc.read("$.corpora[0].latencyMillis.LEXICAL.p95")).isEqualTo(7);
    }

    @Test
    void sameRankingsGiveTheSameResultsWhateverTheLatency() throws IOException {
        Path first = EvaluationReport.write(dir.resolve("a"), run(List.of(5L)));
        Path second = EvaluationReport.write(dir.resolve("b"), run(List.of(900L)));

        DocumentContext a = JsonPath.parse(Files.readString(first));
        DocumentContext b = JsonPath.parse(Files.readString(second));
        a.delete("$.corpora[*].latencyMillis");
        b.delete("$.corpora[*].latencyMillis");
        assertThat(a.jsonString()).isEqualTo(b.jsonString());
    }

    @Test
    void markdownSummarizesEveryVariant() {
        String markdown = EvaluationReport.markdown(run(List.of(5L)));

        assertThat(markdown)
                .contains("| LEXICAL | 2 | 0.500 | 0.500 | 0.500 | 0.250 |")
                .contains("| LEXICAL_PATH | 2 |")
                .contains("## Corpus fixture")
                .contains("| CROSS_FILE_FLOW | 1 |");
    }

    @Test
    void p95OfLatencies() {
        assertThat(EvaluationReport.p95(List.of())).isZero();
        assertThat(EvaluationReport.p95(List.of(3L))).isEqualTo(3);
        assertThat(
                        EvaluationReport.p95(
                                List.of(
                                        20L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L,
                                        14L, 15L, 16L, 17L, 18L, 19L)))
                .isEqualTo(19);
    }

    // Two questions: the first found at rank 2 by LEXICAL and at rank 1 by LEXICAL_PATH, the
    // second missed by LEXICAL.
    private static Run run(List<Long> latency) {
        Question found =
                new Question(
                        "fx-01",
                        "Where is A?",
                        "pt-BR",
                        Category.EXACT_IDENTIFIER,
                        Difficulty.EASY,
                        List.of(new ExpectedRange("src/A.java", 1, 10, List.of())),
                        false,
                        List.of(),
                        null);
        Question missed =
                new Question(
                        "fx-02",
                        "How does B reach C?",
                        "en",
                        Category.CROSS_FILE_FLOW,
                        Difficulty.HARD,
                        List.of(new ExpectedRange("src/B.java", 1, 10, List.of())),
                        false,
                        List.of(),
                        null);
        RetrievedChunk a = new RetrievedChunk(List.of("src/A.java", "src/Copy.java"), 1, 20);
        RetrievedChunk b = new RetrievedChunk(List.of("src/B.java"), 1, 20);
        RetrievedChunk other = new RetrievedChunk(List.of("src/Other.java"), 1, 60);
        Map<String, List<RetrievedChunk>> first = new LinkedHashMap<>();
        first.put("LEXICAL", List.of(other, a));
        first.put("LEXICAL_PATH", List.of(a, other));
        Map<String, List<RetrievedChunk>> second = new LinkedHashMap<>();
        second.put("LEXICAL", List.of(other));
        second.put("LEXICAL_PATH", List.of(b));
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
                        List.of(new QuestionRun(found, first), new QuestionRun(missed, second)),
                        Map.of("LEXICAL", latency));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("provider", null);
        config.put("lexicalVersion", "ident-v1");
        return new Run("2026-10-16", "lexical", config, List.of(corpus));
    }
}
