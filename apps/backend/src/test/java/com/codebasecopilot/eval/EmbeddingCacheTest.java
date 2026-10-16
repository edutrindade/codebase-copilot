package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.EmbeddingResult;
import com.codebasecopilot.providers.InputLimits;
import com.codebasecopilot.providers.ModelInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmbeddingCacheTest {

    @TempDir Path root;

    @Test
    void secondRunReturnsTheSameVectorsWithoutCallingTheProvider() {
        CountingProvider first = new CountingProvider("m");
        List<float[]> before =
                new EmbeddingCache(first, root).embed(List.of("alpha beta", "gamma")).vectors();

        CountingProvider second = new CountingProvider("m");
        EmbeddingCache cache = new EmbeddingCache(second, root);
        EmbeddingResult again = cache.embed(List.of("alpha beta", "gamma"));

        assertThat(again.vectors()).usingRecursiveComparison().isEqualTo(before);
        assertThat(second.requests).isEmpty();
        assertThat(again.tokens()).isZero();
        assertThat(cache.usage()).isEqualTo(new EmbeddingCache.Usage(2, 0, 0));
    }

    @Test
    void sendsOnlyTheMissingTextsInOrder() {
        CountingProvider provider = new CountingProvider("m");
        EmbeddingCache cache = new EmbeddingCache(provider, root);
        cache.embed(List.of("b"));

        EmbeddingResult result = cache.embed(List.of("a", "b", "c"));

        assertThat(provider.requests).containsExactly(List.of("b"), List.of("a", "c"));
        assertThat(result.vectors())
                .usingRecursiveComparison()
                .isEqualTo(new CountingProvider("m").embed(List.of("a", "b", "c")).vectors());
        assertThat(cache.usage()).isEqualTo(new EmbeddingCache.Usage(1, 3, 6));
    }

    @Test
    void modelsDoNotShareVectors() {
        new EmbeddingCache(new CountingProvider("m"), root).embed(List.of("a"));
        CountingProvider other = new CountingProvider("other/m");

        new EmbeddingCache(other, root).embed(List.of("a"));

        assertThat(other.requests).containsExactly(List.of("a"));
        assertThat(root.resolve("fake-other_m-8")).isDirectory();
    }

    @Test
    void storesHashesAndVectorsButNeverTheText() throws IOException {
        new EmbeddingCache(new CountingProvider("m"), root).embed(List.of("secretIdentifier"));

        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                assertThat(file.getFileName().toString()).matches("[0-9a-f]{64}\\.f32");
                assertThat(Files.size(file)).isEqualTo(8 * Float.BYTES);
                assertThat(new String(Files.readAllBytes(file), "ISO-8859-1"))
                        .doesNotContain("secret");
            }
        }
    }

    @Test
    void fileOfTheWrongSizeIsFetchedAgain() throws IOException {
        EmbeddingCache cache = new EmbeddingCache(new CountingProvider("m"), root);
        cache.embed(List.of("a"));
        try (Stream<Path> files = Files.walk(root)) {
            Files.write(files.filter(Files::isRegularFile).findFirst().orElseThrow(), new byte[3]);
        }
        CountingProvider provider = new CountingProvider("m");

        float[] vector =
                new EmbeddingCache(provider, root).embed(List.of("a")).vectors().getFirst();

        assertThat(provider.requests).hasSize(1);
        assertThat(vector).hasSize(8);
    }

    // Vectors derived from the text; two tokens per text, so the usage is easy to check.
    private static final class CountingProvider implements EmbeddingProvider {

        private final ModelInfo model;
        final List<List<String>> requests = new ArrayList<>();

        CountingProvider(String model) {
            this.model = new ModelInfo("fake", model, 8);
        }

        @Override
        public EmbeddingResult embed(List<String> texts) {
            requests.add(List.copyOf(texts));
            List<float[]> vectors = new ArrayList<>();
            for (String text : texts) {
                Random random = new Random(text.hashCode());
                float[] vector = new float[model.dimensions()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = random.nextFloat();
                }
                vectors.add(vector);
            }
            return new EmbeddingResult(vectors, (long) texts.size() * 2);
        }

        @Override
        public ModelInfo model() {
            return model;
        }

        @Override
        public InputLimits limits() {
            return InputLimits.UNBOUNDED;
        }
    }
}
