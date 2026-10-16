package com.codebasecopilot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

// Rebuilds the test roots once per JVM, because Git keeps neither links nor permissions. Each
// context gets its own data directory outside the repository (D-028), so the import recovery of
// one context never removes folders of another.
//
// Tree of the default root:
//   alpha/inner/            listed, has children
//   Beta/file.txt           listed, no subdirectory
//   gamma/link-to-alpha     listed; holds only a link, so no children
//   locked/                 listed, unreadable (POSIX only)
//   many/d1 … many/d6       more directories than app.browse.max-directories
//   readme.txt              file, never listed
//   outside-link            link to ../outside, never listed
//   back\slash, tab\tname   names a client could not send back, never listed (if the FS allows)
//
// Tree of the work root, used by the inventory tests (app.inventory.max-file-bytes is 4 KB):
//   inventory-repo/         src/Main.java, src/util/Helper.kt, README.md and .env.example
//                           included; .env, big.txt (5 KB), link-out (link to ../../outside),
//                           data.bin (binary) and node_modules/ excluded
//   only-excluded/          .env and node_modules/ only
public final class TestFileSystemInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final Path ROOTS_DIR =
            Path.of(System.getProperty("user.dir"), "target", "test-roots").toAbsolutePath();

    static {
        prepareRoots();
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Path dataDir = createDataDir();
        context.getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "testFileSystem",
                                Map.of(
                                        "app.data-dir", dataDir.toString(),
                                        "test.roots-dir", ROOTS_DIR.toString())));
    }

    private static void prepareRoots() {
        try {
            deleteRecursively(ROOTS_DIR);
            buildDefaultRoot(Files.createDirectories(ROOTS_DIR.resolve("default")));
            buildWorkRoot(Files.createDirectories(ROOTS_DIR.resolve("work")));
            Files.createDirectories(ROOTS_DIR.resolve("outside/secret"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path createDataDir() {
        try {
            Path dataDir = Files.createTempDirectory("codebase-copilot-it-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteQuietly(dataDir)));
            return dataDir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void buildDefaultRoot(Path root) throws IOException {
        Files.createDirectories(root.resolve("alpha/inner"));
        Files.createDirectories(root.resolve("Beta"));
        Files.writeString(root.resolve("Beta/file.txt"), "text");
        Files.createDirectories(root.resolve("gamma"));
        Files.createSymbolicLink(root.resolve("gamma/link-to-alpha"), Path.of("../alpha"));
        for (int i = 1; i <= 6; i++) {
            Files.createDirectories(root.resolve("many/d" + i));
        }
        Files.writeString(root.resolve("readme.txt"), "text");
        Files.createSymbolicLink(root.resolve("outside-link"), Path.of("../outside"));
        createIfAllowed(root, "back\\slash");
        createIfAllowed(root, "tab\tname");
        if (isPosix()) {
            Path locked = Files.createDirectory(root.resolve("locked"));
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
            // Otherwise "mvn clean" cannot delete target/ after the tests.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> unlock(locked)));
        }
    }

    private static void buildWorkRoot(Path root) throws IOException {
        Path repo = Files.createDirectories(root.resolve("inventory-repo"));
        Files.createDirectories(repo.resolve("src/util"));
        Files.writeString(repo.resolve("src/Main.java"), "class Main {}");
        Files.writeString(repo.resolve("src/util/Helper.kt"), "object Helper");
        Files.writeString(repo.resolve("README.md"), "# Repo");
        Files.writeString(repo.resolve(".env"), "SECRET=1");
        Files.writeString(repo.resolve(".env.example"), "SECRET=");
        Files.createDirectories(repo.resolve("node_modules/lib"));
        Files.writeString(repo.resolve("node_modules/lib/index.js"), "module.exports = 1");
        Files.writeString(repo.resolve("big.txt"), "x".repeat(5 * 1024));
        Files.write(repo.resolve("data.bin"), new byte[] {1, 0, 2});
        Files.createSymbolicLink(repo.resolve("link-out"), Path.of("../../outside"));
        Path excluded = Files.createDirectories(root.resolve("only-excluded"));
        Files.writeString(excluded.resolve(".env"), "SECRET=1");
        Files.createDirectories(excluded.resolve("node_modules"));
        Files.writeString(excluded.resolve("node_modules/x.js"), "x");
    }

    private static void createIfAllowed(Path root, String name) {
        try {
            Files.createDirectory(root.resolve(name));
        } catch (IOException | InvalidPathException e) {
            // Not every file system accepts these names; the listing test covers the rest.
        }
    }

    private static void unlock(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        } catch (IOException e) {
            // Best effort: the next test run unlocks it before rebuilding the tree.
        }
    }

    private static boolean isPosix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    private static void deleteQuietly(Path path) {
        try {
            deleteRecursively(path);
        } catch (IOException | UncheckedIOException e) {
            // Best effort: the directory is in java.io.tmpdir.
        }
    }

    // Links are deleted, never followed; unreadable directories are unlocked first.
    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(
                path,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc)
                            throws IOException {
                        if (!isPosix()) {
                            throw exc;
                        }
                        Files.setPosixFilePermissions(
                                file, PosixFilePermissions.fromString("rwx------"));
                        deleteRecursively(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        if (exc != null) {
                            throw exc;
                        }
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
