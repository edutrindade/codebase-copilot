package com.codebasecopilot.importjob.scan;

import java.util.regex.Pattern;

// Exclusion pattern syntax (D-036), independent of the OS and case-sensitive:
// "*" matches within a segment, "**" spans segments, "?" is one character other than "/";
// without "/" (other than a trailing one) the pattern matches the name at any depth, otherwise
// it is anchored at the project root; a trailing "/" restricts it to directories.
public final class PathPattern {

    public static final int MAX_LENGTH = 200;

    private final String source;
    private final Pattern regex;
    private final boolean directoriesOnly;
    private final boolean anchored;

    private PathPattern(String source, Pattern regex, boolean directoriesOnly, boolean anchored) {
        this.source = source;
        this.regex = regex;
        this.directoriesOnly = directoriesOnly;
        this.anchored = anchored;
    }

    // Throws IllegalArgumentException for a pattern outside the syntax.
    public static PathPattern compile(String pattern) {
        if (pattern.isEmpty() || pattern.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("length");
        }
        if (pattern.startsWith("!")
                || pattern.chars()
                        .anyMatch(
                                c ->
                                        c == '['
                                                || c == ']'
                                                || c == '\\'
                                                || Character.isISOControl(c))) {
            throw new IllegalArgumentException("unsupported character");
        }
        boolean directoriesOnly = pattern.endsWith("/");
        String body = directoriesOnly ? pattern.substring(0, pattern.length() - 1) : pattern;
        boolean anchored = body.contains("/");
        if (body.startsWith("/")) {
            body = body.substring(1);
        }
        if (body.isEmpty()) {
            throw new IllegalArgumentException("empty");
        }
        for (String segment : body.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("invalid segment");
            }
        }
        return new PathPattern(pattern, Pattern.compile(toRegex(body)), directoriesOnly, anchored);
    }

    // relativePath uses "/" and is NFC-normalized; the root itself is never matched.
    public boolean matches(String relativePath, boolean directory) {
        if (directoriesOnly && !directory) {
            return false;
        }
        if (anchored) {
            return regex.matcher(relativePath).matches();
        }
        int slash = relativePath.lastIndexOf('/');
        return regex.matcher(relativePath.substring(slash + 1)).matches();
    }

    public String source() {
        return source;
    }

    private static String toRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (glob.startsWith("**/", i)) {
                regex.append("(?:[^/]+/)*");
                i += 3;
            } else if (glob.startsWith("/**", i) && i + 3 == glob.length()) {
                regex.append("(?:/.*)?");
                i += 3;
            } else if (glob.startsWith("**", i)) {
                regex.append(".*");
                i += 2;
            } else if (c == '*') {
                regex.append("[^/]*");
                i++;
            } else if (c == '?') {
                regex.append("[^/]");
                i++;
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return regex.toString();
    }
}
