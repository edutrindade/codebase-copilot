package com.codebasecopilot.eval;

import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.EmbeddingResult;
import com.codebasecopilot.providers.InputLimits;
import com.codebasecopilot.providers.ModelInfo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// Keeps every vector the provider returns in a file named by the SHA-256 of the text, one folder
// per model, so a new evaluation run neither pays again nor sees the provider's variation. Only
// hashes and vectors are written, never the text.
public final class EmbeddingCache implements EmbeddingProvider {

    private final EmbeddingProvider delegate;
    private final Path directory;
    private int hits;
    private int misses;
    private long tokens;

    public EmbeddingCache(EmbeddingProvider delegate, Path root) {
        this.delegate = delegate;
        ModelInfo model = delegate.model();
        this.directory =
                root.resolve(
                        (model.provider() + "-" + model.model() + "-" + model.dimensions())
                                .replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    public record Usage(int hits, int misses, long tokens) {}

    @Override
    public synchronized EmbeddingResult embed(List<String> texts) {
        float[][] vectors = new float[texts.size()][];
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            vectors[i] = read(file(texts.get(i)));
            if (vectors[i] == null) {
                missing.add(i);
            }
        }
        hits += texts.size() - missing.size();
        if (missing.isEmpty()) {
            return new EmbeddingResult(List.of(vectors), 0L);
        }
        EmbeddingResult fetched = delegate.embed(missing.stream().map(texts::get).toList());
        for (int j = 0; j < missing.size(); j++) {
            int i = missing.get(j);
            vectors[i] = fetched.vectors().get(j);
            write(file(texts.get(i)), vectors[i]);
        }
        misses += missing.size();
        if (fetched.tokens() != null) {
            tokens += fetched.tokens();
        }
        return new EmbeddingResult(List.of(vectors), fetched.tokens());
    }

    @Override
    public ModelInfo model() {
        return delegate.model();
    }

    @Override
    public InputLimits limits() {
        return delegate.limits();
    }

    public synchronized Usage usage() {
        return new Usage(hits, misses, tokens);
    }

    private Path file(String text) {
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(text.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".f32");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // A file of the wrong size is treated as missing and written again.
    private float @Nullable [] read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            int dimensions = delegate.model().dimensions();
            if (bytes.length != dimensions * Float.BYTES) {
                return null;
            }
            float[] vector = new float[dimensions];
            ByteBuffer.wrap(bytes).asFloatBuffer().get(vector);
            return vector;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Written to a temporary file first, so an interrupted run never leaves half a vector.
    private void write(Path file, float[] vector) {
        ByteBuffer bytes = ByteBuffer.allocate(vector.length * Float.BYTES);
        bytes.asFloatBuffer().put(vector);
        try {
            Files.createDirectories(directory);
            Path temporary = directory.resolve(UUID.randomUUID() + ".tmp");
            Files.write(temporary, bytes.array());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
