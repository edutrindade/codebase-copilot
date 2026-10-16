package com.codebasecopilot.indexing.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.indexing.content.TextClassifier.Text;
import com.codebasecopilot.indexing.content.TextClassifier.Unsupported;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TextClassifierTest {

    static Stream<Arguments> texts() {
        return Stream.of(
                Arguments.of("", List.of()),
                Arguments.of("a", List.of("a")),
                Arguments.of("a\nb", List.of("a", "b")),
                Arguments.of("a\nb\n", List.of("a", "b")),
                Arguments.of("a\r\nb\r\n", List.of("a", "b")),
                Arguments.of("a\rb", List.of("a", "b")),
                Arguments.of("a\n\n\nb", List.of("a", "", "", "b")),
                Arguments.of("\n", List.of("")),
                Arguments.of("olá, ação ✓", List.of("olá, ação ✓")));
    }

    @ParameterizedTest
    @MethodSource("texts")
    void splitsLinesAndKeepsContent(String content, List<String> lines) {
        assertThat(TextClassifier.classify(content.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(new Text(content, lines));
    }

    @Test
    void removesUtf8Bom() {
        byte[] bytes = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i'};

        assertThat(TextClassifier.classify(bytes)).isEqualTo(new Text("hi", List.of("hi")));
    }

    @Test
    void invalidUtf8IsUnsupported() {
        byte[] latin1 = "ação".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(TextClassifier.classify(latin1))
                .isEqualTo(new Unsupported(UnsupportedReason.INVALID_ENCODING));
    }

    @Test
    void nulAnywhereIsBinary() {
        byte[] bytes = new byte[20_000];
        Arrays.fill(bytes, (byte) 'a');
        bytes[19_000] = 0;

        assertThat(TextClassifier.classify(bytes))
                .isEqualTo(new Unsupported(UnsupportedReason.BINARY));
    }

    @Test
    void lineAboveTheChunkLimitIsTooLong() {
        String limit = "x".repeat(LineChunker.MAX_CHARS);

        assertThat(TextClassifier.classify(("a\n" + limit).getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(Text.class);
        assertThat(TextClassifier.classify(("a\n" + limit + "x").getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(new Unsupported(UnsupportedReason.LINE_TOO_LONG));
    }
}
