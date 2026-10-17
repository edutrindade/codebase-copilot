package com.codebasecopilot.indexing.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.indexing.content.TextClassifier.Text;
import com.codebasecopilot.indexing.content.TextClassifier.Unsupported;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"UTF-16", "UTF-16LE", "UTF-16BE"})
    void utf16WithBomIsAnUnsupportedEncodingNotBinary(String charset) {
        byte[] content = "class A {}\n".getBytes(Charset.forName(charset));
        byte[] bytes =
                charset.equals("UTF-16LE")
                        ? concat(new byte[] {(byte) 0xFF, (byte) 0xFE}, content)
                        : charset.equals("UTF-16BE")
                                ? concat(new byte[] {(byte) 0xFE, (byte) 0xFF}, content)
                                : content;

        assertThat(TextClassifier.classify(bytes))
                .isEqualTo(new Unsupported(UnsupportedReason.INVALID_ENCODING));
    }

    @Test
    void utf16WithoutBomIsBinary() {
        assertThat(TextClassifier.classify("class A {}".getBytes(StandardCharsets.UTF_16LE)))
                .isEqualTo(new Unsupported(UnsupportedReason.BINARY));
    }

    @Test
    void bomKeepsTheLinesOfTheFile() {
        byte[] content = "a\nb\n".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, content);

        assertThat(TextClassifier.classify(bytes)).isEqualTo(new Text("a\nb\n", List.of("a", "b")));
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

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] bytes = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, bytes, first.length, second.length);
        return bytes;
    }
}
