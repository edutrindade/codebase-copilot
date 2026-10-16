package com.codebasecopilot.codeanalysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

// Words of a question that may name a file (D-058): paths and file names are matched by
// trigram similarity; names are kept only if a file of the snapshot has that name.
public final class PathTerms {

    private static final Pattern FILE_NAME =
            Pattern.compile("[\\p{L}\\p{N}_.-]*\\.\\p{L}[\\p{L}\\p{N}]{0,9}");
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}_-]{3,}");

    public record Terms(List<String> paths, List<String> names) {}

    private PathTerms() {}

    public static Terms of(String question) {
        Map<String, String> paths = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (String raw : question.split("\\s+")) {
            String token = trim(raw);
            String key = token.toLowerCase(Locale.ROOT);
            if (isPath(token)) {
                paths.putIfAbsent(key, token);
            } else if (NAME.matcher(token).matches() && !Stopwords.contains(key)) {
                names.putIfAbsent(key, token);
            }
        }
        return new Terms(List.copyOf(paths.values()), List.copyOf(names.values()));
    }

    // Drops quotes, brackets and sentence punctuation around the token, and a leading "./" or
    // "/" (paths are relative to the project).
    private static String trim(String token) {
        int start = 0;
        int end = token.length();
        while (start < end && !isPathChar(token.charAt(start))) {
            start++;
        }
        while (end > start
                && (!isPathChar(token.charAt(end - 1)) || token.charAt(end - 1) == '.')) {
            end--;
        }
        String trimmed = token.substring(start, end);
        while (trimmed.startsWith("./") || trimmed.startsWith("/")) {
            trimmed = trimmed.substring(trimmed.indexOf('/') + 1);
        }
        return trimmed;
    }

    private static boolean isPath(String token) {
        if (token.isEmpty() || !token.chars().allMatch(c -> isPathChar((char) c))) {
            return false;
        }
        if (token.indexOf('/') >= 0) {
            return token.chars().anyMatch(Character::isLetterOrDigit);
        }
        return FILE_NAME.matcher(token).matches();
    }

    private static boolean isPathChar(char c) {
        return Character.isLetterOrDigit(c) || c == '/' || c == '.' || c == '_' || c == '-';
    }
}
