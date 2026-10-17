package com.codebasecopilot.codeanalysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Splits code identifiers the way the PostgreSQL "simple" parser does not (D-017, D-055).
// Terms keep their original case: PostgreSQL lowercases them in to_tsvector/to_tsquery, and its
// lowercasing differs from Java's for some letters.
public final class IdentifierTokenizer {

    public static final String VERSION = "ident-v1";

    private enum CharClass {
        UPPER,
        LOWER,
        DIGIT,
        OTHER
    }

    private record Word(String text, int start, int end, boolean inAngleBrackets) {}

    private IdentifierTokenizer() {}

    // Terms a chunk adds on top of its content: compound identifiers and their parts; words
    // touching another word without whitespace, which the parser may keep as one lexeme (paths,
    // URLs, e-mails, versions); and words between "<" and ">", which the parser may read as a
    // tag without lexemes (List<String>, <T extends Number>, HTML).
    public static List<String> identifierTerms(String text) {
        Terms terms = new Terms();
        List<Word> words = words(text);
        for (int i = 0; i < words.size(); i++) {
            Word word = words.get(i);
            List<String> parts = parts(word.text());
            if (parts.size() > 1) {
                terms.add(word.text());
                parts.forEach(terms::add);
            } else if (word.inAngleBrackets() || glued(text, words, i)) {
                terms.add(word.text());
            }
        }
        return terms.list();
    }

    // Every word, compound and part of a question, without stopwords.
    public static List<String> queryTerms(String text) {
        Terms terms = new Terms();
        for (Word word : words(text)) {
            List<String> parts = parts(word.text());
            if (parts.size() > 1) {
                terms.add(word.text());
            }
            parts.forEach(terms::add);
        }
        return terms.list().stream().filter(term -> !Stopwords.contains(key(term))).toList();
    }

    private static List<Word> words(String text) {
        List<Word> words = new ArrayList<>();
        int start = -1;
        boolean startInAngleBrackets = false;
        boolean inAngleBrackets = false;
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            boolean wordChar = isWordChar(codePoint);
            if (wordChar && start < 0) {
                start = i;
                startInAngleBrackets = inAngleBrackets;
            } else if (!wordChar) {
                if (start >= 0) {
                    words.add(new Word(text.substring(start, i), start, i, startInAngleBrackets));
                    start = -1;
                }
                if (codePoint == '<') {
                    inAngleBrackets = true;
                } else if (codePoint == '>') {
                    inAngleBrackets = false;
                }
            }
            i += Character.charCount(codePoint);
        }
        if (start >= 0) {
            words.add(new Word(text.substring(start), start, text.length(), startInAngleBrackets));
        }
        return words;
    }

    private static boolean glued(String text, List<Word> words, int index) {
        Word word = words.get(index);
        return (index > 0 && noWhitespace(text, words.get(index - 1).end(), word.start()))
                || (index < words.size() - 1
                        && noWhitespace(text, word.end(), words.get(index + 1).start()));
    }

    private static boolean noWhitespace(String text, int from, int to) {
        return text.substring(from, to)
                .codePoints()
                .noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
    }

    // getUserById -> get User By Id; HTTPClient -> HTTP Client; parseJSON2Xml -> parse JSON 2 Xml.
    private static List<String> parts(String word) {
        int[] codePoints = word.codePoints().toArray();
        CharClass[] classes = new CharClass[codePoints.length];
        for (int i = 0; i < codePoints.length; i++) {
            classes[i] = isMark(codePoints[i]) && i > 0 ? classes[i - 1] : classOf(codePoints[i]);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < codePoints.length; i++) {
            if (!isMark(codePoints[i]) && boundary(classes, i)) {
                parts.add(new String(codePoints, start, i - start));
                start = i;
            }
        }
        parts.add(new String(codePoints, start, codePoints.length - start));
        return parts;
    }

    private static boolean boundary(CharClass[] classes, int i) {
        CharClass previous = classes[i - 1];
        CharClass current = classes[i];
        if ((previous == CharClass.DIGIT) != (current == CharClass.DIGIT)) {
            return true;
        }
        if (previous == CharClass.LOWER && current == CharClass.UPPER) {
            return true;
        }
        return previous == CharClass.UPPER
                && current == CharClass.UPPER
                && i + 1 < classes.length
                && classes[i + 1] == CharClass.LOWER;
    }

    private static CharClass classOf(int codePoint) {
        if (Character.isDigit(codePoint)) {
            return CharClass.DIGIT;
        }
        if (Character.isUpperCase(codePoint) || Character.isTitleCase(codePoint)) {
            return CharClass.UPPER;
        }
        if (Character.isLowerCase(codePoint)) {
            return CharClass.LOWER;
        }
        return CharClass.OTHER;
    }

    private static boolean isWordChar(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || isMark(codePoint);
    }

    private static boolean isMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK;
    }

    private static String key(String term) {
        return term.toLowerCase(Locale.ROOT);
    }

    private static final class Terms {

        private final Map<String, String> byKey = new LinkedHashMap<>();

        void add(String term) {
            byKey.putIfAbsent(key(term), term);
        }

        List<String> list() {
            return List.copyOf(byKey.values());
        }
    }
}
