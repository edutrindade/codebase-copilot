package com.codebasecopilot.eval;

import com.codebasecopilot.providers.GenerationModel;
import com.codebasecopilot.providers.GenerationProvider;
import com.codebasecopilot.providers.GenerationRequest;
import com.codebasecopilot.providers.GenerationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

// Keeps every result the chat provider returns in a file named by the SHA-256 of the request, one
// folder per model and settings, so the answer metrics can be computed again without paying and
// without the model's variation. The file holds the model output (the answer text), never the
// prompt; it lives in the local data directory, like the answers the application stores.
public final class GenerationCache implements GenerationProvider {

    // The prompt delimiters carry a random nonce per request (PromptBuilder); without
    // normalizing it, no request would ever hit the cache.
    private static final Pattern NONCE = Pattern.compile("\\b[0-9a-f]{32}(?=>>>)");

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    private final GenerationProvider delegate;
    private final Path directory;
    private int hits;
    private int misses;
    private long inputTokens;
    private long outputTokens;

    // settings: what changes the output besides the model, such as the reasoning effort.
    public GenerationCache(GenerationProvider delegate, Path root, String settings) {
        this.delegate = delegate;
        GenerationModel model = delegate.model();
        String name = model.provider() + "-" + model.model();
        if (!settings.isBlank()) {
            name += "-" + settings;
        }
        this.directory = root.resolve("answers").resolve(name.replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    // Tokens billed by the provider, that is, of the misses only.
    public record Usage(int hits, int misses, long inputTokens, long outputTokens) {}

    @Override
    public synchronized GenerationResult generate(GenerationRequest request) {
        Path file = file(request);
        GenerationResult cached = read(file);
        if (cached != null) {
            hits++;
            return cached;
        }
        GenerationResult result = delegate.generate(request);
        write(file, result);
        misses++;
        inputTokens += result.inputTokens() == null ? 0 : result.inputTokens();
        outputTokens += result.outputTokens() == null ? 0 : result.outputTokens();
        return result;
    }

    @Override
    public GenerationModel model() {
        return delegate.model();
    }

    public synchronized Usage usage() {
        return new Usage(hits, misses, inputTokens, outputTokens);
    }

    private Path file(GenerationRequest request) {
        String key =
                String.join(
                        "\0",
                        normalized(request.instructions()),
                        normalized(request.input()),
                        request.schema().name(),
                        JSON.writeValueAsString(request.schema().jsonSchema()));
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(key.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".json");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalized(String text) {
        return NONCE.matcher(text).replaceAll("NONCE");
    }

    // A file that cannot be read as a result is treated as missing and written again.
    private static @Nullable GenerationResult read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonNode root = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (root == null || !root.path("output").isString()) {
                return null;
            }
            return new GenerationResult(
                    root.get("output").stringValue(),
                    number(root, "inputTokens"),
                    number(root, "outputTokens"),
                    number(root, "reasoningTokens"));
        } catch (JacksonException e) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static @Nullable Long number(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isNumber() ? value.longValue() : null;
    }

    // Written to a temporary file first, so an interrupted run never leaves half a result.
    private void write(Path file, GenerationResult result) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("output", result.output());
        content.put("inputTokens", result.inputTokens());
        content.put("outputTokens", result.outputTokens());
        content.put("reasoningTokens", result.reasoningTokens());
        try {
            Files.createDirectories(directory);
            Path temporary = directory.resolve(UUID.randomUUID() + ".tmp");
            Files.writeString(temporary, JSON.writeValueAsString(content), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
