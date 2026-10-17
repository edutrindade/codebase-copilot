package com.codebasecopilot.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// Deterministic source tree for the performance harness (P6 of Etapa 8): Java, TypeScript, Python
// and Markdown files whose terms and identifiers are skewed towards the first ones, so a few are
// in almost every chunk and most are rare. Nothing in it is meant to compile.
public final class SyntheticRepository {

    public record Spec(int files, int minLines, int maxLines, int vocabulary, long seed) {

        // About 3,000 files and 50,000 chunks of lines-v1 (60 lines with an overlap of 10).
        public static final Spec DEFAULT = new Spec(3_000, 200, 1_480, 5_000, 42);
    }

    public record Stats(int files, long bytes, long lines) {}

    private static final String[] SYLLABLES = {
        "ka", "lo", "mi", "re", "su", "ta", "ne", "po", "vi", "da", "ge", "ru", "zo", "fi", "ba",
        "xe"
    };
    private static final String[] EXTENSIONS = {"java", "java", "java", "ts", "ts", "py", "md"};
    private static final int MODULES = 50;

    private SyntheticRepository() {}

    public static Stats write(Path root, Spec spec) throws IOException {
        long bytes = 0;
        long lines = 0;
        for (int i = 0; i < spec.files(); i++) {
            Random random = new Random(spec.seed() * 1_000_003 + i);
            int count = spec.minLines() + random.nextInt(spec.maxLines() - spec.minLines() + 1);
            byte[] content = content(random, extension(i), count, spec.vocabulary());
            Path file = root.resolve(path(i));
            Files.createDirectories(file.getParent());
            Files.write(file, content);
            bytes += content.length;
            lines += count;
        }
        return new Stats(spec.files(), bytes, lines);
    }

    // Rewrites a fraction of the files, a different set in each round; returns their paths.
    public static List<String> mutate(Path root, Spec spec, int round, double fraction)
            throws IOException {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < spec.files(); i++) {
            indexes.add(i);
        }
        Collections.shuffle(indexes, new Random(spec.seed() + 7_919L * (round + 1)));
        int count = (int) Math.round(spec.files() * fraction);
        List<String> changed = new ArrayList<>();
        for (int i : indexes.subList(0, count)) {
            Random random = new Random(spec.seed() * 1_000_003 + i + 104_729L * (round + 1));
            int lines = spec.minLines() + random.nextInt(spec.maxLines() - spec.minLines() + 1);
            Files.write(
                    root.resolve(path(i)), content(random, extension(i), lines, spec.vocabulary()));
            changed.add(path(i));
        }
        return changed;
    }

    // Questions in four shapes, in turn: a common term with a rare one, two rare terms, an
    // identifier and a file name.
    public static List<String> questions(Spec spec, int count) {
        Random random = new Random(spec.seed());
        List<String> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String rare = rare(random, spec);
            questions.add(
                    switch (i % 4) {
                        case 0 -> "where is " + word(random.nextInt(20)) + " used with " + rare;
                        case 1 -> "how does " + rare + " call " + rare(random, spec);
                        case 2 ->
                                "where is "
                                        + word(random.nextInt(200))
                                        + capitalize(word(random.nextInt(200)))
                                        + " assigned";
                        default -> {
                            int file = random.nextInt(spec.files());
                            yield "what does " + capitalize(word(file % 400)) + file + " do";
                        }
                    });
        }
        return questions;
    }

    // One term that is in almost every chunk, the worst case of the lexical channel.
    public static List<String> commonTermQuestions(Spec spec, int count) {
        Random random = new Random(spec.seed() + 1);
        List<String> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            questions.add("what is " + word(random.nextInt(3)));
        }
        return questions;
    }

    // From the last thousand words, or the last tenth of a small vocabulary.
    private static String rare(Random random, Spec spec) {
        return word(
                spec.vocabulary()
                        - 1
                        - random.nextInt(Math.max(1, Math.min(1_000, spec.vocabulary() / 10))));
    }

    public static String word(int index) {
        StringBuilder word = new StringBuilder();
        int n = index;
        do {
            word.append(SYLLABLES[n % SYLLABLES.length]);
            n /= SYLLABLES.length;
        } while (n > 0);
        return word.append(SYLLABLES[index % 7]).toString();
    }

    static String path(int file) {
        String name = capitalize(word(file % 400)) + file;
        return "src/module"
                + (file % MODULES)
                + "/"
                + word(file % 13)
                + "/"
                + name
                + "."
                + extension(file);
    }

    private static String extension(int file) {
        return EXTENSIONS[file % EXTENSIONS.length];
    }

    // Shaped like the corpora (3 to 5 tokens per line, 11% to 20% blank): statements with an
    // occasional literal, comments, closing braces and blank lines.
    private static byte[] content(Random random, String extension, int lines, int vocabulary) {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < lines; line++) {
            int shape = random.nextInt(100);
            String a = identifier(random, vocabulary);
            String b = identifier(random, vocabulary);
            String c = term(random, vocabulary);
            String argument = random.nextInt(10) < 3 ? Integer.toString(random.nextInt(100)) : a;
            String code =
                    switch (extension) {
                        case "java" ->
                                "        var " + a + " = " + b + "." + c + "(" + argument + ");";
                        case "ts" -> "    const " + a + " = " + b + "." + c + "(" + argument + ")";
                        case "py" -> "        " + a + " = " + b + "." + c + "(" + argument + ")";
                        default -> "The " + c + " step calls " + a + ".";
                    };
            String comment =
                    (extension.equals("py") ? "# " : "// ") + c + " " + term(random, vocabulary);
            text.append(
                            shape < 15
                                    ? ""
                                    : shape < 25 && !extension.equals("md")
                                            ? (extension.equals("py") ? "" : "    }")
                                            : shape < 32 && !extension.equals("md")
                                                    ? "    " + comment
                                                    : code)
                    .append('\n');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    // From a fixed pool of four identifiers per word, skewed like the words: a repository reuses
    // its identifiers, and random pairs would make almost every one unique.
    private static String identifier(Random random, int vocabulary) {
        int index = (int) Math.floor(4.0 * vocabulary * Math.pow(random.nextDouble(), 3));
        return word(index % vocabulary)
                + capitalize(word((int) ((index * 7_919L + 13) % vocabulary)));
    }

    private static String term(Random random, int vocabulary) {
        return word((int) Math.floor(vocabulary * Math.pow(random.nextDouble(), 3)));
    }

    private static String capitalize(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
