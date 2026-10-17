package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.providers.GenerationFailure;
import com.codebasecopilot.providers.GenerationModel;
import com.codebasecopilot.providers.GenerationProvider;
import com.codebasecopilot.providers.GenerationRequest;
import com.codebasecopilot.providers.GenerationRequest.OutputSchema;
import com.codebasecopilot.providers.GenerationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerationCacheTest {

    private static final OutputSchema SCHEMA =
            new OutputSchema("answer", Map.of("type", "object", "required", List.of("a")));

    @TempDir Path root;

    @Test
    void secondRunReturnsTheSameResultWithoutCallingTheProvider() {
        CountingProvider first = new CountingProvider("m");
        GenerationResult before =
                new GenerationCache(first, root, "").generate(request("Refund?", nonce('a')));

        CountingProvider second = new CountingProvider("m");
        GenerationCache cache = new GenerationCache(second, root, "");
        GenerationResult again = cache.generate(request("Refund?", nonce('a')));

        assertThat(again).isEqualTo(before);
        assertThat(second.requests).isEmpty();
        assertThat(cache.usage()).isEqualTo(new GenerationCache.Usage(1, 0, 0, 0));
    }

    @Test
    void theNonceOfThePromptDoesNotChangeTheKey() {
        CountingProvider provider = new CountingProvider("m");
        GenerationCache cache = new GenerationCache(provider, root, "");

        cache.generate(request("Refund?", nonce('a')));
        cache.generate(request("Refund?", nonce('b')));

        assertThat(provider.requests).hasSize(1);
        assertThat(cache.usage()).isEqualTo(new GenerationCache.Usage(1, 1, 7, 3));
    }

    @Test
    void anotherQuestionModelOrSettingIsAMiss() {
        new GenerationCache(new CountingProvider("m"), root, "reasoning-low")
                .generate(request("Refund?", nonce('a')));

        CountingProvider question = new CountingProvider("m");
        new GenerationCache(question, root, "reasoning-low").generate(request("Fee?", nonce('a')));
        CountingProvider model = new CountingProvider("other/m");
        new GenerationCache(model, root, "reasoning-low").generate(request("Refund?", nonce('a')));
        CountingProvider effort = new CountingProvider("m");
        new GenerationCache(effort, root, "reasoning-none")
                .generate(request("Refund?", nonce('a')));

        assertThat(question.requests).hasSize(1);
        assertThat(model.requests).hasSize(1);
        assertThat(effort.requests).hasSize(1);
        assertThat(root.resolve("answers/fake-other_m-reasoning-low")).isDirectory();
    }

    @Test
    void storesTheOutputButNeverThePrompt() throws IOException {
        new GenerationCache(new CountingProvider("m"), root, "")
                .generate(request("Refund?", nonce('a')));

        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                assertThat(file.getFileName().toString()).matches("[0-9a-f]{64}\\.json");
                assertThat(Files.readString(file))
                        .contains("\"output\"")
                        .doesNotContain("<<<BEGIN QUESTION")
                        .doesNotContain("Answer only");
            }
        }
    }

    @Test
    void unreadableFileIsFetchedAgain() throws IOException {
        new GenerationCache(new CountingProvider("m"), root, "")
                .generate(request("Refund?", nonce('a')));
        try (Stream<Path> files = Files.walk(root)) {
            Files.writeString(
                    files.filter(Files::isRegularFile).findFirst().orElseThrow(), "{\"out");
        }
        CountingProvider provider = new CountingProvider("m");

        GenerationResult result =
                new GenerationCache(provider, root, "").generate(request("Refund?", nonce('a')));

        assertThat(provider.requests).hasSize(1);
        assertThat(result.output()).isEqualTo("{\"a\":\"Refund?\"}");
    }

    @Test
    void failuresAreNotCached() {
        GenerationProvider failing =
                new CountingProvider("m") {
                    @Override
                    public GenerationResult generate(GenerationRequest request) {
                        super.generate(request);
                        throw new GenerationFailure(GenerationFailure.Type.UNAVAILABLE, "down");
                    }
                };
        GenerationCache cache = new GenerationCache(failing, root, "");

        assertThatThrownBy(() -> cache.generate(request("Refund?", nonce('a'))))
                .isInstanceOf(GenerationFailure.class);
        CountingProvider provider = new CountingProvider("m");
        new GenerationCache(provider, root, "").generate(request("Refund?", nonce('a')));

        assertThat(provider.requests).hasSize(1);
        assertThat(cache.usage()).isEqualTo(new GenerationCache.Usage(0, 0, 0, 0));
    }

    private static String nonce(char c) {
        return String.valueOf(c).repeat(32);
    }

    // Shaped like the prompt of answering: the nonce closes every delimiter.
    private static GenerationRequest request(String question, String nonce) {
        return new GenerationRequest(
                "Answer only from blocks delimited by <<<BEGIN {kind} " + nonce + ">>>.",
                "<<<BEGIN QUESTION "
                        + nonce
                        + ">>>\n"
                        + question
                        + "\n<<<END QUESTION "
                        + nonce
                        + ">>>",
                SCHEMA);
    }

    // Echoes the question; 7 input and 3 output tokens per call.
    private static class CountingProvider implements GenerationProvider {

        private final GenerationModel model;
        final List<GenerationRequest> requests = new ArrayList<>();

        CountingProvider(String model) {
            this.model = new GenerationModel("fake", model);
        }

        @Override
        public GenerationResult generate(GenerationRequest request) {
            requests.add(request);
            String question = request.input().split("\n")[1];
            return new GenerationResult("{\"a\":\"" + question + "\"}", 7L, 3L, null);
        }

        @Override
        public GenerationModel model() {
            return model;
        }
    }
}
