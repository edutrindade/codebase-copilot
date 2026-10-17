package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

// The external corpora are not in the repository; their annotations are checked against the line
// counts recorded in the manifests.
class EvalAnnotationsTest {

    static final Path REPOSITORY_ROOT = Path.of("../..").toAbsolutePath().normalize();
    static final Path QUESTIONS = REPOSITORY_ROOT.resolve("eval/questions");

    static Stream<Path> questionFiles() throws IOException {
        try (Stream<Path> files = Files.list(QUESTIONS)) {
            return files.filter(f -> f.toString().endsWith(".yaml")).sorted().toList().stream();
        }
    }

    @Test
    void everyCorpusHasAQuestionFile() throws IOException {
        assertThat(questionFiles().map(f -> f.getFileName().toString()))
                .containsExactly(
                        "click.yaml", "codebase-copilot.yaml", "fixture.yaml", "hono.yaml");
    }

    @ParameterizedTest
    @MethodSource("questionFiles")
    void annotationsPointToExistingFilesAndLines(Path file) throws IOException {
        QuestionSet set = QuestionSets.load(file);

        List<String> problems = QuestionSets.validate(set, set.manifest(REPOSITORY_ROOT));

        assertThat(problems).isEmpty();
        assertThat(file.getFileName().toString()).isEqualTo(set.corpus().name() + ".yaml");
    }

    @Test
    void fixtureMatchesItsManifest() throws IOException {
        QuestionSet fixture = QuestionSets.load(QUESTIONS.resolve("fixture.yaml"));

        assertThat(fixture.verifiedDirectory(REPOSITORY_ROOT, name -> null))
                .isEqualTo(REPOSITORY_ROOT.resolve("eval/corpus/fixture"));
    }

    // Otherwise a typo in the annotation would make the leak check pass on every answer. Paths
    // count, since they reach the model too; the fixture has files in Latin-1 and UTF-16 on
    // purpose, hence the lenient decoding.
    @Test
    void forbiddenTextsOfTheFixtureAppearInIt() throws IOException {
        QuestionSet fixture = QuestionSets.load(QUESTIONS.resolve("fixture.yaml"));
        String corpus;
        Path root = REPOSITORY_ROOT.resolve("eval/corpus/fixture");
        try (Stream<Path> files = Files.walk(root)) {
            StringBuilder all = new StringBuilder();
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                all.append(root.relativize(file)).append('\n');
                all.append(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                        .append('\n');
            }
            corpus = all.toString();
        }

        List<String> forbidden =
                fixture.questions().stream().flatMap(q -> q.mustNotContain().stream()).toList();

        assertThat(forbidden).isNotEmpty();
        assertThat(forbidden).allSatisfy(text -> assertThat(corpus).containsIgnoringCase(text));
    }
}
