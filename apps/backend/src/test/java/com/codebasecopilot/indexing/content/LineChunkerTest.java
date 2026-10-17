package com.codebasecopilot.indexing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.codebasecopilot.indexing.content.LineChunker.Chunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LineChunkerTest {

    static IntStream seeds() {
        return IntStream.range(0, 200);
    }

    @ParameterizedTest
    @MethodSource("seeds")
    void holdsTheChunkingProperties(int seed) {
        List<String> lines = generate(new Random(seed));

        List<Chunk> chunks = LineChunker.chunk(lines);

        boolean[] covered = new boolean[lines.size()];
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            assertThat(chunk.ordinal()).isEqualTo(i);
            assertThat(chunk.startLine()).isBetween(1, chunk.endLine());
            assertThat(chunk.endLine()).isLessThanOrEqualTo(lines.size());
            assertThat(chunk.content())
                    .isEqualTo(
                            String.join(
                                    "\n", lines.subList(chunk.startLine() - 1, chunk.endLine())));
            assertThat(chunk.endLine() - chunk.startLine() + 1)
                    .isLessThanOrEqualTo(LineChunker.MAX_LINES);
            assertThat(chunk.content().length()).isLessThanOrEqualTo(LineChunker.MAX_CHARS);
            assertThat(lines.get(chunk.startLine() - 1)).isNotBlank();
            assertThat(lines.get(chunk.endLine() - 1)).isNotBlank();
            assertThat(chunk.tokenCountEstimate()).isEqualTo((chunk.content().length() + 3) / 4);
            for (int line = chunk.startLine(); line <= chunk.endLine(); line++) {
                covered[line - 1] = true;
            }
            if (i > 0) {
                Chunk previous = chunks.get(i - 1);
                assertThat(chunk.startLine()).isGreaterThan(previous.startLine());
                assertThat(previous.endLine() - chunk.startLine() + 1)
                        .isLessThanOrEqualTo(LineChunker.OVERLAP_LINES);
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                assertThat(covered[i]).as("line %d", i + 1).isTrue();
            }
        }
        assertThat(LineChunker.chunk(lines)).isEqualTo(chunks);
    }

    @Test
    void singleLine() {
        assertThat(LineChunker.chunk(List.of("hello")))
                .singleElement()
                .satisfies(
                        chunk -> {
                            assertThat(chunk.startLine()).isEqualTo(1);
                            assertThat(chunk.endLine()).isEqualTo(1);
                            assertThat(chunk.content()).isEqualTo("hello");
                        });
    }

    @Test
    void blankOnlyTextHasNoChunks() {
        assertThat(LineChunker.chunk(List.of())).isEmpty();
        assertThat(LineChunker.chunk(List.of("", "  ", "\t"))).isEmpty();
    }

    @Test
    void longFileOverlapsByTenLines() {
        List<String> lines = numbered(130);

        List<Chunk> chunks = LineChunker.chunk(lines);

        assertThat(chunks)
                .extracting(Chunk::startLine, Chunk::endLine)
                .containsExactly(tuple(1, 60), tuple(51, 110), tuple(101, 130));
    }

    @Test
    void prefersToCutAtABlankLineNearTheEnd() {
        List<String> lines = numbered(100);
        lines.set(54, "");

        List<Chunk> chunks = LineChunker.chunk(lines);

        assertThat(chunks.get(0).endLine()).isEqualTo(54);
        assertThat(chunks.get(1).startLine()).isEqualTo(56);
    }

    @Test
    void ignoresBlankLinesBeforeTheWindow() {
        List<String> lines = numbered(100);
        lines.set(20, "");

        assertThat(LineChunker.chunk(lines).get(0).endLine()).isEqualTo(60);
    }

    @Test
    void cutsByCharacters() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            lines.add("x".repeat(999));
        }

        List<Chunk> chunks = LineChunker.chunk(lines);

        assertThat(chunks.get(0).endLine()).isEqualTo(4);
        assertThat(chunks.get(0).content()).hasSize(4 * 999 + 3);
    }

    @Test
    void hashDependsOnVersionAndTextOnly() {
        Chunk chunk = LineChunker.chunk(List.of("a", "b")).getFirst();

        assertThat(chunk.chunkHash()).isEqualTo(ContentHash.of(LineChunker.VERSION + "\na\nb"));
        assertThat(LineChunker.chunk(List.of("a", "c")).getFirst().chunkHash())
                .isNotEqualTo(chunk.chunkHash());
    }

    @Test
    void otherParametersChangeSizeOverlapAndHash() {
        List<String> lines = numbered(130);

        List<Chunk> withoutOverlap =
                LineChunker.chunk(lines, new LineChunker.Parameters("lines-40-0", 40, 0));
        List<Chunk> wideOverlap =
                LineChunker.chunk(lines, new LineChunker.Parameters("lines-80-20", 80, 20));

        assertThat(withoutOverlap)
                .extracting(Chunk::startLine, Chunk::endLine)
                .containsExactly(tuple(1, 40), tuple(41, 80), tuple(81, 120), tuple(121, 130));
        assertThat(wideOverlap)
                .extracting(Chunk::startLine, Chunk::endLine)
                .containsExactly(tuple(1, 80), tuple(61, 130));
        assertThat(withoutOverlap.getFirst().chunkHash())
                .isEqualTo(
                        ContentHash.of("lines-40-0\n" + String.join("\n", lines.subList(0, 40))));
    }

    @Test
    void defaultParametersAreLinesV1() {
        List<String> lines = numbered(130);

        assertThat(LineChunker.chunk(lines, LineChunker.V1)).isEqualTo(LineChunker.chunk(lines));
        assertThat(LineChunker.V1).isEqualTo(new LineChunker.Parameters("lines-v1", 60, 10));
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new LineChunker.Parameters(" ", 60, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LineChunker.Parameters("v", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LineChunker.Parameters("v", 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LineChunker.Parameters("v", 10, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLinesAboveTheLimit() {
        assertThatThrownBy(() -> LineChunker.chunk(List.of("x".repeat(LineChunker.MAX_CHARS + 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> numbered(int count) {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            lines.add("line " + i);
        }
        return lines;
    }

    // Mixes short and long lines with runs of blank lines.
    private static List<String> generate(Random random) {
        int count = random.nextInt(400);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int kind = random.nextInt(10);
            if (kind < 2) {
                lines.add(random.nextBoolean() ? "" : "   ");
            } else if (kind == 9) {
                lines.add("y".repeat(1 + random.nextInt(LineChunker.MAX_CHARS)));
            } else {
                lines.add("x".repeat(1 + random.nextInt(120)));
            }
        }
        return lines;
    }
}
