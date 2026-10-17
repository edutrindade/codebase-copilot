package com.codebasecopilot.eval;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

// JDK-only on purpose: `java CorpusManifest.java <dir>` prints the manifest of a corpus directory.
public record CorpusManifest(List<Entry> entries) {

    public static final int NOT_TEXT = -1;

    private static final int MAX_REPORTED_DIFFERENCES = 20;

    public record Entry(String path, String sha256, int lines) {}

    public CorpusManifest {
        entries = entries.stream().sorted(Comparator.comparing(Entry::path)).toList();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: java CorpusManifest.java <corpus-dir>");
            System.exit(2);
        }
        System.out.print(scan(Path.of(args[0])).format());
    }

    public static CorpusManifest scan(Path dir) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path file : paths.toList()) {
                if (Files.isSymbolicLink(file)) {
                    throw new IllegalStateException(
                            "corpus contains a symbolic link: " + relative(dir, file));
                }
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] bytes = Files.readAllBytes(file);
                    entries.add(new Entry(relative(dir, file), sha256(bytes), lines(bytes)));
                }
            }
        }
        return new CorpusManifest(entries);
    }

    public static CorpusManifest read(Path manifest) throws IOException {
        List<Entry> entries = new ArrayList<>();
        int number = 0;
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            number++;
            String[] parts = line.split(" ", 3);
            if (parts.length != 3 || !parts[0].matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(manifest + ":" + number + ": malformed entry");
            }
            int lines = parts[1].equals("-") ? NOT_TEXT : Integer.parseInt(parts[1]);
            entries.add(new Entry(parts[2], parts[0], lines));
        }
        return new CorpusManifest(entries);
    }

    public String format() {
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            out.append(entry.sha256())
                    .append(' ')
                    .append(entry.lines() == NOT_TEXT ? "-" : Integer.toString(entry.lines()))
                    .append(' ')
                    .append(entry.path())
                    .append('\n');
        }
        return out.toString();
    }

    public Optional<Entry> entry(String path) {
        return entries.stream().filter(e -> e.path().equals(path)).findFirst();
    }

    public List<String> differences(CorpusManifest actual) {
        Map<String, Entry> expected = byPath(entries);
        Map<String, Entry> found = byPath(actual.entries());
        List<String> differences = new ArrayList<>();
        for (Entry entry : entries) {
            Entry other = found.get(entry.path());
            if (other == null) {
                differences.add("missing: " + entry.path());
            } else if (!other.sha256().equals(entry.sha256())) {
                differences.add("changed: " + entry.path());
            }
        }
        for (Entry entry : actual.entries()) {
            if (!expected.containsKey(entry.path())) {
                differences.add("unexpected: " + entry.path());
            }
        }
        return differences;
    }

    public void verify(Path dir) throws IOException {
        List<String> differences = differences(scan(dir));
        if (!differences.isEmpty()) {
            throw new IllegalStateException(
                    "corpus at "
                            + dir
                            + " differs from its manifest ("
                            + differences.size()
                            + " differences): "
                            + String.join(
                                    ", ",
                                    differences.subList(
                                            0,
                                            Math.min(
                                                    differences.size(),
                                                    MAX_REPORTED_DIFFERENCES))));
        }
    }

    // Same rules as TextClassifier: NUL byte or invalid UTF-8 is not text; \n, \r\n and \r end a
    // line, and a final line terminator does not start a new line.
    static int lines(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return NOT_TEXT;
            }
        }
        String content;
        try {
            content =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
        } catch (CharacterCodingException e) {
            return NOT_TEXT;
        }
        int lines = 0;
        int i = 0;
        boolean open = false;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (c == '\n' || c == '\r') {
                lines++;
                open = false;
                i += c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n' ? 2 : 1;
            } else {
                open = true;
                i++;
            }
        }
        return open ? lines + 1 : lines;
    }

    private static Map<String, Entry> byPath(List<Entry> entries) {
        Map<String, Entry> map = new LinkedHashMap<>();
        entries.forEach(e -> map.put(e.path(), e));
        return map;
    }

    private static String relative(Path dir, Path file) {
        return dir.relativize(file).toString().replace('\\', '/');
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
