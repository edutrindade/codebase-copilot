package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.codebasecopilot.indexing.content.TextClassifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CorpusManifestTest {

    @TempDir Path dir;

    static Stream<byte[]> texts() {
        return Stream.of(
                        "",
                        "a",
                        "a\n",
                        "a\nb",
                        "a\r\nb\r\n",
                        "a\rb",
                        "\n\n",
                        "a\n\n\nb\n",
                        "﻿first\nsecond",
                        "ação\n")
                .map(s -> s.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @MethodSource("texts")
    void countsLinesLikeTheIndexer(byte[] bytes) {
        var text = (TextClassifier.Text) TextClassifier.classify(bytes);

        assertThat(CorpusManifest.lines(bytes)).isEqualTo(text.lines().size());
    }

    @Test
    void binaryAndInvalidUtf8AreNotText() {
        assertThat(CorpusManifest.lines(new byte[] {'a', 0, 'b'}))
                .isEqualTo(CorpusManifest.NOT_TEXT);
        assertThat(CorpusManifest.lines(new byte[] {'a', (byte) 0xC3}))
                .isEqualTo(CorpusManifest.NOT_TEXT);
    }

    @Test
    void formatRoundTripsSortedByPath(@TempDir Path out) throws IOException {
        write("b.txt", "one\ntwo\n");
        write("a/z.bin", "x\0y");
        write("a/c.md", "# title");

        CorpusManifest manifest = CorpusManifest.scan(dir);
        Path file = out.resolve("manifest.sha256");
        Files.writeString(file, manifest.format());

        assertThat(manifest.entries())
                .extracting(CorpusManifest.Entry::path, CorpusManifest.Entry::lines)
                .containsExactly(
                        tuple("a/c.md", 1),
                        tuple("a/z.bin", CorpusManifest.NOT_TEXT),
                        tuple("b.txt", 2));
        assertThat(manifest.format()).contains(" - a/z.bin\n");
        assertThat(CorpusManifest.read(file)).isEqualTo(manifest);
    }

    @Test
    void verifyAcceptsAnIdenticalDirectory() throws IOException {
        write("src/Main.java", "class Main {}\n");
        CorpusManifest manifest = CorpusManifest.scan(dir);

        manifest.verify(dir);
    }

    @Test
    void verifyStopsOnChangedMissingOrUnexpectedFiles() throws IOException {
        write("changed.txt", "before\n");
        write("removed.txt", "gone\n");
        CorpusManifest manifest = CorpusManifest.scan(dir);

        write("changed.txt", "after\n");
        Files.delete(dir.resolve("removed.txt"));
        write("extra/new.txt", "new\n");

        assertThatThrownBy(() -> manifest.verify(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 differences")
                .hasMessageContaining("changed: changed.txt")
                .hasMessageContaining("missing: removed.txt")
                .hasMessageContaining("unexpected: extra/new.txt");
    }

    @Test
    void rejectsSymbolicLinks() throws IOException {
        write("target.txt", "x\n");
        Files.createSymbolicLink(dir.resolve("link.txt"), dir.resolve("target.txt"));

        assertThatThrownBy(() -> CorpusManifest.scan(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("link.txt");
    }

    @Test
    void rejectsMalformedManifest(@TempDir Path out) throws IOException {
        Path file = out.resolve("manifest.sha256");
        Files.writeString(file, "not-a-hash 3 a.txt\n");

        assertThatThrownBy(() -> CorpusManifest.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(":1: malformed entry");
    }

    private void write(String path, String content) throws IOException {
        Path file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
