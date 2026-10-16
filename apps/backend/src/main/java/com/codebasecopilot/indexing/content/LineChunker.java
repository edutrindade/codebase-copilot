package com.codebasecopilot.indexing.content;

import java.util.ArrayList;
import java.util.List;

// Generic line-based chunking, provisional values of D-043 (O-010). Any change to the
// parameters or the algorithm requires a new VERSION.
public final class LineChunker {

    public static final String VERSION = "lines-v1";
    public static final int MAX_LINES = 60;
    public static final int MAX_CHARS = 4000;
    public static final int OVERLAP_LINES = 10;
    static final int BLANK_LINE_WINDOW = 15;

    private LineChunker() {}

    // Lines are 1-based; content joins the lines with \n.
    public record Chunk(
            int ordinal,
            int startLine,
            int endLine,
            String content,
            String chunkHash,
            int tokenCountEstimate) {}

    public static List<Chunk> chunk(List<String> lines) {
        for (String line : lines) {
            if (line.length() > MAX_CHARS) {
                throw new IllegalArgumentException("line longer than " + MAX_CHARS);
            }
        }
        List<Chunk> chunks = new ArrayList<>();
        int last = lastNonBlank(lines);
        int start = nextNonBlank(lines, 0);
        while (start >= 0 && start <= last) {
            int end = hardEnd(lines, start);
            boolean softCut = false;
            if (end < last) {
                for (int k = end; k > start && k > end - BLANK_LINE_WINDOW; k--) {
                    if (lines.get(k).isBlank()) {
                        end = k - 1;
                        softCut = true;
                        break;
                    }
                }
            }
            while (lines.get(end).isBlank()) {
                end--;
            }
            chunks.add(chunk(chunks.size(), lines, start, end));
            if (end >= last) {
                break;
            }
            int size = end - start + 1;
            int next =
                    softCut ? end + 1 : Math.max(end + 1 - OVERLAP_LINES, start + (size + 1) / 2);
            start = nextNonBlank(lines, next);
        }
        return chunks;
    }

    // Largest end (inclusive) within the line and character limits.
    private static int hardEnd(List<String> lines, int start) {
        int end = start;
        int chars = lines.get(start).length();
        while (end + 1 < lines.size()
                && end + 1 - start < MAX_LINES
                && chars + 1 + lines.get(end + 1).length() <= MAX_CHARS) {
            end++;
            chars += 1 + lines.get(end).length();
        }
        return end;
    }

    private static Chunk chunk(int ordinal, List<String> lines, int start, int end) {
        String content = String.join("\n", lines.subList(start, end + 1));
        return new Chunk(
                ordinal,
                start + 1,
                end + 1,
                content,
                ContentHash.of(VERSION + "\n" + content),
                (content.length() + 3) / 4);
    }

    private static int nextNonBlank(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private static int lastNonBlank(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (!lines.get(i).isBlank()) {
                return i;
            }
        }
        return -1;
    }
}
