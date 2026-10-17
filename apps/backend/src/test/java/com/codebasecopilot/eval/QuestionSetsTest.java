package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.eval.QuestionSet.Category;
import com.codebasecopilot.eval.QuestionSet.Difficulty;
import com.codebasecopilot.eval.QuestionSet.ExpectedRange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestionSetsTest {

    private static final String CORPUS =
            """
            corpus:
              name: sample
              directory: corpus
              manifest: corpus.sha256
            """;

    @TempDir Path root;

    @Test
    void loadsQuestionsWithRangesAndOtherPaths() throws IOException {
        QuestionSet set =
                load(
                        CORPUS
                                + """
                                questions:
                                  - id: q-1
                                    question: Where is foo?
                                    language: en
                                    category: EXACT_IDENTIFIER
                                    difficulty: EASY
                                    expected:
                                      - path: a.txt
                                        startLine: 1
                                        endLine: 2
                                        otherPaths: [b.txt]
                                  - id: q-2
                                    question: Qual broker?
                                    language: pt-BR
                                    category: UNANSWERABLE
                                    difficulty: MEDIUM
                                    mustAbstain: true
                                    notes: sem resposta
                                """);

        assertThat(set.corpus().name()).isEqualTo("sample");
        assertThat(set.corpus().directory()).isEqualTo("corpus");
        assertThat(set.questions()).hasSize(2);
        var first = set.questions().getFirst();
        assertThat(first.category()).isEqualTo(Category.EXACT_IDENTIFIER);
        assertThat(first.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(first.mustAbstain()).isFalse();
        assertThat(first.expected())
                .containsExactly(new ExpectedRange("a.txt", 1, 2, List.of("b.txt")));
        assertThat(set.questions().get(1).mustAbstain()).isTrue();
        assertThat(set.questions().get(1).expected()).isEmpty();
    }

    @Test
    void rejectsUnknownKeys() {
        assertThatThrownBy(() -> load(CORPUS + question("    startline: 3\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("q-1: unknown key startline");
    }

    @Test
    void rejectsUnknownLanguageCategoryAndDifficulty() {
        assertThatThrownBy(
                        () -> load(CORPUS + question("").replace("language: en", "language: pt")))
                .hasMessageContaining("unknown language pt");
        assertThatThrownBy(() -> load(CORPUS + question("").replace("EXACT_IDENTIFIER", "SYNONYM")))
                .hasMessageContaining("unknown Category SYNONYM");
        assertThatThrownBy(() -> load(CORPUS + question("").replace("EASY", "TRIVIAL")))
                .hasMessageContaining("unknown Difficulty TRIVIAL");
    }

    @Test
    void rejectsMissingFieldsAndDuplicateKeys() {
        assertThatThrownBy(() -> load(CORPUS + question("").replace("    question: q\n", "")))
                .hasMessageContaining("q-1: question is required");
        assertThatThrownBy(() -> load(CORPUS + question("    language: en\n")))
                .hasMessageContaining("duplicate key");
    }

    @Test
    void requiresExactlyOneCorpusLocation() {
        String both = CORPUS.replace("  manifest:", "  directoryEnv: X\n  manifest:");

        assertThatThrownBy(() -> load(both + question("")))
                .hasMessageContaining("exactly one of directory and directoryEnv");
    }

    @Test
    void validatorReportsRangesOutsideTheCorpus() throws IOException {
        CorpusManifest manifest =
                new CorpusManifest(
                        List.of(
                                entry("a.txt", 10),
                                entry("short.txt", 3),
                                entry("image.png", CorpusManifest.NOT_TEXT)));
        QuestionSet set =
                load(
                        CORPUS
                                + """
                                questions:
                                  - id: ok
                                    question: q
                                    language: en
                                    category: CONCEPTUAL
                                    difficulty: EASY
                                    expected:
                                      - {path: a.txt, startLine: 1, endLine: 10}
                                  - id: past-end
                                    question: q
                                    language: en
                                    category: CONCEPTUAL
                                    difficulty: EASY
                                    expected:
                                      - {path: a.txt, startLine: 5, endLine: 11}
                                  - id: reversed
                                    question: q
                                    language: en
                                    category: CONCEPTUAL
                                    difficulty: EASY
                                    expected:
                                      - {path: a.txt, startLine: 4, endLine: 3}
                                  - id: missing
                                    question: q
                                    language: en
                                    category: CONCEPTUAL
                                    difficulty: EASY
                                    expected:
                                      - {path: b.txt, startLine: 1, endLine: 1}
                                  - id: binary
                                    question: q
                                    language: en
                                    category: CONCEPTUAL
                                    difficulty: EASY
                                    expected:
                                      - {path: image.png, startLine: 1, endLine: 1}
                                  - id: other-short
                                    question: q
                                    language: en
                                    category: CONCEPTUAL
                                    difficulty: EASY
                                    expected:
                                      - {path: a.txt, startLine: 1, endLine: 5, otherPaths: [short.txt]}
                                  - id: no-ranges
                                    question: q
                                    language: en
                                    category: CONCEPTUAL
                                    difficulty: EASY
                                  - id: abstain-with-ranges
                                    question: q
                                    language: en
                                    category: CONCEPTUAL
                                    difficulty: EASY
                                    mustAbstain: true
                                    expected:
                                      - {path: a.txt, startLine: 1, endLine: 1}
                                  - id: unanswerable
                                    question: q
                                    language: en
                                    category: UNANSWERABLE
                                    difficulty: EASY
                                    expected:
                                      - {path: a.txt, startLine: 1, endLine: 1}
                                  - id: ok
                                    question: q
                                    language: en
                                    category: CONCEPTUAL
                                    difficulty: EASY
                                    expected:
                                      - {path: a.txt, startLine: 1, endLine: 1}
                                """);

        List<String> problems = QuestionSets.validate(set, manifest);

        assertThat(problems)
                .containsExactly(
                        "past-end: a.txt has 10 lines, range ends at 11",
                        "reversed: invalid range 4-3 in a.txt",
                        "missing: b.txt is not in the corpus",
                        "binary: image.png is not a text file",
                        "other-short: short.txt has 3 lines, range ends at 5",
                        "no-ranges: expected ranges are required unless mustAbstain is true, and"
                                + " forbidden when it is",
                        "abstain-with-ranges: expected ranges are required unless mustAbstain is"
                                + " true, and forbidden when it is",
                        "unanswerable: UNANSWERABLE requires mustAbstain",
                        "ok: duplicate id");
    }

    @Test
    void verifiedDirectoryStopsWhenTheCorpusDiffersFromTheManifest() throws IOException {
        Path corpus = Files.createDirectories(root.resolve("corpus"));
        Files.writeString(corpus.resolve("a.txt"), "one\n");
        Files.writeString(root.resolve("corpus.sha256"), CorpusManifest.scan(corpus).format());
        QuestionSet set = load(CORPUS + question(""));

        assertThat(set.verifiedDirectory(root, name -> null)).isEqualTo(corpus);

        Files.writeString(corpus.resolve("a.txt"), "two\n");

        assertThatThrownBy(() -> set.verifiedDirectory(root, name -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed: a.txt");
    }

    @Test
    void verifiedDirectoryReadsTheEnvironmentVariable() throws IOException {
        Path corpus = Files.createDirectories(root.resolve("elsewhere"));
        Files.writeString(corpus.resolve("a.txt"), "one\n");
        Files.writeString(root.resolve("corpus.sha256"), CorpusManifest.scan(corpus).format());
        String external =
                CORPUS.replace("  directory: corpus", "  directoryEnv: EVAL_CORPUS_SAMPLE")
                        .replace("  manifest:", "  commit: \"abc\"\n  manifest:");
        QuestionSet set = load(external + question(""));
        Map<String, String> env = Map.of("EVAL_CORPUS_SAMPLE", corpus.toString());

        assertThat(set.verifiedDirectory(root, env::get)).isEqualTo(corpus);
        assertThatThrownBy(() -> set.verifiedDirectory(root, name -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("set EVAL_CORPUS_SAMPLE")
                .hasMessageContaining("commit abc");
    }

    private static String question(String extra) {
        return """
                questions:
                  - id: q-1
                    question: q
                    language: en
                    category: EXACT_IDENTIFIER
                    difficulty: EASY
                    expected:
                      - {path: a.txt, startLine: 1, endLine: 1}
                """
                + extra;
    }

    private static CorpusManifest.Entry entry(String path, int lines) {
        return new CorpusManifest.Entry(path, "0".repeat(64), lines);
    }

    private QuestionSet load(String yaml) throws IOException {
        Path file = root.resolve("questions.yaml");
        Files.writeString(file, yaml);
        return QuestionSets.load(file);
    }
}
