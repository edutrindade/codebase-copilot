package com.codebasecopilot.indexing.content;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Decides whether a file is indexable text (D-042, D-043). Lines are split on \n, \r\n and \r;
// a final line break does not start a new line.
public final class TextClassifier {

    private TextClassifier() {}

    public sealed interface Classification {}

    // content keeps the original line breaks; lines do not include them.
    public record Text(String content, List<String> lines) implements Classification {}

    public record Unsupported(UnsupportedReason reason) implements Classification {}

    public static Classification classify(byte[] bytes) {
        // UTF-16 is not decoded; without this check its NUL bytes would make it BINARY.
        if (hasUtf16Bom(bytes)) {
            return new Unsupported(UnsupportedReason.INVALID_ENCODING);
        }
        for (byte b : bytes) {
            if (b == 0) {
                return new Unsupported(UnsupportedReason.BINARY);
            }
        }
        int offset = hasBom(bytes) ? 3 : 0;
        String content;
        try {
            content =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                            .toString();
        } catch (CharacterCodingException e) {
            return new Unsupported(UnsupportedReason.INVALID_ENCODING);
        }
        List<String> lines = lines(content);
        for (String line : lines) {
            if (line.length() > LineChunker.MAX_CHARS) {
                return new Unsupported(UnsupportedReason.LINE_TOO_LONG);
            }
        }
        return new Text(content, List.copyOf(lines));
    }

    static List<String> lines(String content) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (c == '\n' || c == '\r') {
                lines.add(content.substring(start, i));
                i += c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n' ? 2 : 1;
                start = i;
            } else {
                i++;
            }
        }
        if (start < content.length()) {
            lines.add(content.substring(start));
        }
        return lines;
    }

    private static boolean hasUtf16Bom(byte[] bytes) {
        return bytes.length >= 2
                && (((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE)
                        || ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF));
    }

    private static boolean hasBom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }
}
