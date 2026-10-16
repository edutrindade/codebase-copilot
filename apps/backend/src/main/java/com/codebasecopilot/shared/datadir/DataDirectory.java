package com.codebasecopilot.shared.datadir;

import com.codebasecopilot.shared.localroots.LocalRoot;
import com.codebasecopilot.shared.localroots.LocalRoots;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// The path never leaves the backend (D-028).
public final class DataDirectory {

    static final String DEFAULT = "~/.codebase-copilot";

    private final Path imports;

    private DataDirectory(Path imports) {
        this.imports = imports;
    }

    // Throws IllegalStateException so the application does not start with an invalid value.
    public static DataDirectory from(
            @Nullable String configured, LocalRoots roots, Path userHome, Path workingDirectory) {
        String value = configured == null || configured.isBlank() ? DEFAULT : configured.strip();
        // Validated before anything is created, so an invalid value leaves no folder behind.
        Path path = realPathAllowingMissing(expandHome(value, userHome));
        for (LocalRoot root : roots.list()) {
            Path rootPath = realPath(root.path());
            if (path.startsWith(rootPath) || rootPath.startsWith(path)) {
                throw invalid(
                        "must not be inside local root '"
                                + root.key()
                                + "' nor contain it (D-028, D-029)");
            }
        }
        Optional<Path> repository = enclosingRepository(realPath(workingDirectory));
        if (repository.isPresent() && path.startsWith(repository.get())) {
            throw invalid("must be outside the Codebase Copilot repository");
        }
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw invalid("must be a directory that exists or can be created");
        }
        if (!Files.isReadable(path) || !Files.isWritable(path)) {
            throw invalid("must be readable and writable");
        }
        Path imports = path.resolve("imports");
        try {
            Files.createDirectories(imports);
        } catch (IOException e) {
            throw invalid("cannot create the imports directory");
        }
        return new DataDirectory(imports);
    }

    public Path importsDir() {
        return imports;
    }

    public Path importDir(UUID importJobId) {
        return imports.resolve(importJobId.toString());
    }

    // Extracted tree of a ZIP import, the source of its project while it is current (D-033).
    public Path importSourceDir(UUID importJobId) {
        return importDir(importJobId).resolve("source");
    }

    private static Path expandHome(String value, Path userHome) {
        String expanded;
        if (value.equals("~")) {
            expanded = userHome.toString();
        } else if (value.startsWith("~/")) {
            expanded = userHome + value.substring(1);
        } else if (value.startsWith("~")) {
            throw invalid("'~user' is not supported; use '~' or an absolute path");
        } else {
            expanded = value;
        }
        Path path;
        try {
            path = Path.of(expanded);
        } catch (InvalidPathException e) {
            throw invalid("is not a valid path");
        }
        if (!path.isAbsolute()) {
            throw invalid("must be absolute");
        }
        return path.normalize();
    }

    // Missing segments cannot be links, so resolving the deepest existing ancestor is enough.
    private static Path realPathAllowingMissing(Path path) {
        Path existing = path;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            throw invalid("cannot be resolved");
        }
        return realPath(existing).resolve(existing.relativize(path));
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw invalid("cannot be resolved");
        }
    }

    // Worktrees and submodules use a .git file instead of a directory.
    private static Optional<Path> enclosingRepository(Path directory) {
        for (Path current = directory; current != null; current = current.getParent()) {
            if (Files.exists(current.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
                return Optional.of(current);
            }
        }
        return Optional.empty();
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Invalid app.data-dir (DATA_DIR): " + reason + " (D-028)");
    }
}
