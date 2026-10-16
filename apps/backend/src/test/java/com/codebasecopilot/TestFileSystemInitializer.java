package com.codebasecopilot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

// Test roots are rebuilt once per JVM, because Git keeps neither links nor permissions; the data
// directory lives outside the repository, as the application requires (D-028).
public final class TestFileSystemInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final Path ROOTS_DIR =
            Path.of(System.getProperty("user.dir"), "target", "test-roots").toAbsolutePath();

    private static final Path DATA_DIR = prepare();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "testFileSystem",
                                Map.of(
                                        "app.data-dir", DATA_DIR.toString(),
                                        "test.roots-dir", ROOTS_DIR.toString())));
    }

    private static Path prepare() {
        try {
            deleteRecursively(ROOTS_DIR);
            Files.createDirectories(ROOTS_DIR.resolve("default"));
            Files.createDirectories(ROOTS_DIR.resolve("work"));
            Path dataDir = Files.createTempDirectory("codebase-copilot-it-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteQuietly(dataDir)));
            return dataDir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            deleteRecursively(path);
        } catch (IOException | UncheckedIOException e) {
            // Best effort: the directory is in java.io.tmpdir.
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
