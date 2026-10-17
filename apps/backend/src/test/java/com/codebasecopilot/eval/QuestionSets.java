package com.codebasecopilot.eval;

import com.codebasecopilot.eval.QuestionSet.Category;
import com.codebasecopilot.eval.QuestionSet.Corpus;
import com.codebasecopilot.eval.QuestionSet.Difficulty;
import com.codebasecopilot.eval.QuestionSet.ExpectedRange;
import com.codebasecopilot.eval.QuestionSet.Question;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class QuestionSets {

    private static final Set<String> CORPUS_KEYS =
            Set.of(
                    "name",
                    "directory",
                    "directoryEnv",
                    "repository",
                    "commit",
                    "license",
                    "manifest");
    private static final Set<String> QUESTION_KEYS =
            Set.of(
                    "id",
                    "question",
                    "language",
                    "category",
                    "difficulty",
                    "expected",
                    "mustAbstain",
                    "mustNotContain",
                    "notes");
    private static final Set<String> RANGE_KEYS =
            Set.of("path", "startLine", "endLine", "otherPaths");

    private QuestionSets() {}

    public static QuestionSet load(Path file) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            document = new Yaml(new SafeConstructor(options)).load(reader);
        }
        try {
            Map<String, Object> root = map(document, "document", Set.of("corpus", "questions"));
            Corpus corpus = corpus(map(root.get("corpus"), "corpus", CORPUS_KEYS));
            List<Question> questions = new ArrayList<>();
            for (Object item : list(root.get("questions"), "questions")) {
                questions.add(question(item));
            }
            return new QuestionSet(corpus, List.copyOf(questions));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(file + ": " + e.getMessage(), e);
        }
    }

    public static List<String> validate(QuestionSet set, CorpusManifest manifest) {
        List<String> problems = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Question q : set.questions()) {
            if (!ids.add(q.id())) {
                problems.add(q.id() + ": duplicate id");
            }
            if (q.mustAbstain() != q.expected().isEmpty()) {
                problems.add(
                        q.id()
                                + ": expected ranges are required unless mustAbstain is true, and"
                                + " forbidden when it is");
            }
            if (q.category() == Category.UNANSWERABLE && !q.mustAbstain()) {
                problems.add(q.id() + ": UNANSWERABLE requires mustAbstain");
            }
            if ((q.category() == Category.PROMPT_INJECTION
                            || q.category() == Category.EXCLUDED_SECRET)
                    && q.mustNotContain().isEmpty()) {
                problems.add(q.id() + ": " + q.category() + " requires mustNotContain");
            }
            for (ExpectedRange range : q.expected()) {
                if (range.startLine() < 1 || range.startLine() > range.endLine()) {
                    problems.add(
                            q.id()
                                    + ": invalid range "
                                    + range.startLine()
                                    + "-"
                                    + range.endLine()
                                    + " in "
                                    + range.path());
                }
                List<String> paths = new ArrayList<>(List.of(range.path()));
                paths.addAll(range.otherPaths());
                for (String path : paths) {
                    var entry = manifest.entry(path);
                    if (entry.isEmpty()) {
                        problems.add(q.id() + ": " + path + " is not in the corpus");
                    } else if (entry.get().lines() == CorpusManifest.NOT_TEXT) {
                        problems.add(q.id() + ": " + path + " is not a text file");
                    } else if (range.endLine() > entry.get().lines()) {
                        problems.add(
                                q.id()
                                        + ": "
                                        + path
                                        + " has "
                                        + entry.get().lines()
                                        + " lines, range ends at "
                                        + range.endLine());
                    }
                }
            }
        }
        return problems;
    }

    private static Corpus corpus(Map<String, Object> fields) {
        String directory = optionalText(fields, "directory", "corpus");
        String directoryEnv = optionalText(fields, "directoryEnv", "corpus");
        if ((directory == null) == (directoryEnv == null)) {
            throw new IllegalArgumentException(
                    "corpus: exactly one of directory and directoryEnv is required");
        }
        return new Corpus(
                text(fields, "name", "corpus"),
                directory,
                directoryEnv,
                optionalText(fields, "repository", "corpus"),
                optionalText(fields, "commit", "corpus"),
                optionalText(fields, "license", "corpus"),
                text(fields, "manifest", "corpus"));
    }

    private static Question question(Object item) {
        String where =
                item instanceof Map<?, ?> m && m.get("id") instanceof String s ? s : "question";
        Map<String, Object> fields = map(item, where, QUESTION_KEYS);
        String id = text(fields, "id", where);
        String language = text(fields, "language", id);
        if (!QuestionSet.LANGUAGES.contains(language)) {
            throw new IllegalArgumentException(id + ": unknown language " + language);
        }
        List<ExpectedRange> expected = new ArrayList<>();
        Object ranges = fields.get("expected");
        if (ranges != null) {
            for (Object range : list(ranges, id + ".expected")) {
                expected.add(range(range, id));
            }
        }
        Object mustAbstain = fields.getOrDefault("mustAbstain", false);
        if (!(mustAbstain instanceof Boolean)) {
            throw new IllegalArgumentException(id + ": mustAbstain must be true or false");
        }
        return new Question(
                id,
                text(fields, "question", id),
                language,
                constant(Category.class, text(fields, "category", id), id),
                constant(Difficulty.class, text(fields, "difficulty", id), id),
                List.copyOf(expected),
                (Boolean) mustAbstain,
                texts(fields, "mustNotContain", id, "texts"),
                optionalText(fields, "notes", id));
    }

    private static ExpectedRange range(Object item, String id) {
        Map<String, Object> fields = map(item, id + ".expected", RANGE_KEYS);
        return new ExpectedRange(
                text(fields, "path", id),
                integer(fields, "startLine", id),
                integer(fields, "endLine", id),
                texts(fields, "otherPaths", id, "paths"));
    }

    private static List<String> texts(
            Map<String, Object> fields, String key, String id, String what) {
        Object value = fields.get(key);
        if (value == null) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        for (Object item : list(value, id + "." + key)) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(id + ": " + key + " must be " + what);
            }
            texts.add(text);
        }
        return List.copyOf(texts);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(@Nullable Object value, String where, Set<String> keys) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(where + ": expected a mapping");
        }
        for (Object key : map.keySet()) {
            if (!keys.contains(key)) {
                throw new IllegalArgumentException(where + ": unknown key " + key);
            }
        }
        return (Map<String, Object>) map;
    }

    private static List<?> list(@Nullable Object value, String where) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(where + ": expected a list");
        }
        return list;
    }

    private static String text(Map<String, Object> fields, String key, String where) {
        String value = optionalText(fields, key, where);
        if (value == null) {
            throw new IllegalArgumentException(where + ": " + key + " is required");
        }
        return value;
    }

    private static @Nullable String optionalText(
            Map<String, Object> fields, String key, String where) {
        Object value = fields.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(where + ": " + key + " must be non-blank text");
        }
        return text.strip();
    }

    private static int integer(Map<String, Object> fields, String key, String where) {
        if (!(fields.get(key) instanceof Integer value)) {
            throw new IllegalArgumentException(where + ": " + key + " must be an integer");
        }
        return value;
    }

    private static <E extends Enum<E>> E constant(Class<E> type, String value, String where) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    where + ": unknown " + type.getSimpleName() + " " + value, e);
        }
    }
}
