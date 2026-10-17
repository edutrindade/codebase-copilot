package com.codebasecopilot.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public record QuestionSet(Corpus corpus, List<Question> questions) {

    public enum Category {
        EXACT_IDENTIFIER,
        CONCEPTUAL,
        CROSS_FILE_FLOW,
        ARCHITECTURE,
        DEPENDENCIES_CONFIGURATION,
        AMBIGUOUS,
        UNANSWERABLE,
        PROMPT_INJECTION,
        EXCLUDED_SECRET
    }

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public static final List<String> LANGUAGES = List.of("pt-BR", "en");

    // Exactly one of directory (relative to the repository root) or directoryEnv is set.
    public record Corpus(
            String name,
            @Nullable String directory,
            @Nullable String directoryEnv,
            @Nullable String repository,
            @Nullable String commit,
            @Nullable String license,
            String manifest) {}

    public record Question(
            String id,
            String question,
            String language,
            Category category,
            Difficulty difficulty,
            List<ExpectedRange> expected,
            boolean mustAbstain,
            @Nullable String notes) {}

    // A chunk is relevant when it overlaps the range in path or in any of otherPaths (same
    // content).
    public record ExpectedRange(String path, int startLine, int endLine, List<String> otherPaths) {}

    public CorpusManifest manifest(Path repositoryRoot) throws IOException {
        return CorpusManifest.read(repositoryRoot.resolve(corpus.manifest()));
    }

    public Path verifiedDirectory(
            Path repositoryRoot, Function<String, @Nullable String> environment)
            throws IOException {
        Path dir;
        if (corpus.directory() != null) {
            dir = repositoryRoot.resolve(corpus.directory());
        } else {
            String value = environment.apply(corpus.directoryEnv());
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "set "
                                + corpus.directoryEnv()
                                + " to the extracted "
                                + corpus.name()
                                + " corpus at commit "
                                + corpus.commit());
            }
            dir = Path.of(value);
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("corpus directory not found: " + dir);
        }
        manifest(repositoryRoot).verify(dir);
        return dir;
    }
}
