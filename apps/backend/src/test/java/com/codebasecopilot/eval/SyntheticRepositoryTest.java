package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticRepositoryTest {

    private static final SyntheticRepository.Spec SMALL =
            new SyntheticRepository.Spec(40, 10, 30, 200, 7);

    @TempDir Path dir;

    @Test
    void sameSpecWritesTheSameTree() throws IOException {
        SyntheticRepository.Stats first = SyntheticRepository.write(dir.resolve("a"), SMALL);
        SyntheticRepository.write(dir.resolve("b"), SMALL);

        assertThat(first.files()).isEqualTo(40);
        assertThat(CorpusManifest.scan(dir.resolve("a")))
                .isEqualTo(CorpusManifest.scan(dir.resolve("b")));
        assertThat(CorpusManifest.scan(dir.resolve("a")).entries())
                .hasSize(40)
                .allSatisfy(e -> assertThat(e.lines()).isBetween(10, 30));
        assertThat(first.lines())
                .isEqualTo(
                        CorpusManifest.scan(dir.resolve("a")).entries().stream()
                                .mapToLong(CorpusManifest.Entry::lines)
                                .sum());
    }

    @Test
    void eachRoundRewritesADifferentFractionOfTheFiles() throws IOException {
        SyntheticRepository.write(dir, SMALL);
        CorpusManifest before = CorpusManifest.scan(dir);

        List<String> first = SyntheticRepository.mutate(dir, SMALL, 0, 0.1);
        List<String> second = SyntheticRepository.mutate(dir, SMALL, 1, 0.1);

        assertThat(first).hasSize(4);
        assertThat(second).hasSize(4).isNotEqualTo(first);
        Set<String> changed = new HashSet<>(first);
        changed.addAll(second);
        assertThat(before.differences(CorpusManifest.scan(dir)))
                .hasSize(changed.size())
                .allSatisfy(d -> assertThat(d).startsWith("changed: "));
    }

    @Test
    void questionsAreDeterministicAndUseWordsOfTheVocabulary() {
        List<String> questions = SyntheticRepository.questions(SMALL, 8);

        assertThat(questions).hasSize(8).isEqualTo(SyntheticRepository.questions(SMALL, 8));
        assertThat(questions.get(0)).startsWith("where is ").contains(" used with ");
        assertThat(questions.get(1)).startsWith("how does ").contains(" call ");
        assertThat(questions.get(3)).matches("what does [A-Z][a-z]+\\d+ do");
        assertThat(SyntheticRepository.commonTermQuestions(SMALL, 5))
                .hasSize(5)
                .allSatisfy(
                        q ->
                                assertThat(q)
                                        .isIn(
                                                "what is " + SyntheticRepository.word(0),
                                                "what is " + SyntheticRepository.word(1),
                                                "what is " + SyntheticRepository.word(2)));
    }

    @Test
    void wordsAreDistinct() {
        assertThat(IntStream.range(0, 5_000).mapToObj(SyntheticRepository::word).distinct())
                .hasSize(5_000);
    }

    @Test
    void pathsAreDistinctAndHaveSourceExtensions() throws IOException {
        SyntheticRepository.write(dir, SMALL);

        try (var files = Files.walk(dir)) {
            assertThat(files.filter(Files::isRegularFile).map(f -> f.getFileName().toString()))
                    .hasSize(40)
                    .allMatch(name -> name.matches(".+\\.(java|ts|py|md)"));
        }
    }
}
