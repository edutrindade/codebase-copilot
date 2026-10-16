package com.codebasecopilot.importjob.browse;

import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.error.FieldViolation;
import com.codebasecopilot.shared.fs.ConfinedPaths;
import com.codebasecopilot.shared.fs.RelativePaths;
import com.codebasecopilot.shared.localroots.LocalRoot;
import com.codebasecopilot.shared.localroots.LocalRoots;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

// Lists only real subdirectories: links are never listed nor followed (D-018).
@Service
@EnableConfigurationProperties(BrowseProperties.class)
class DirectoryBrowser {

    private static final Comparator<String> BY_NAME =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

    private final LocalRoots roots;
    private final int maxDirectories;

    DirectoryBrowser(LocalRoots roots, BrowseProperties properties) {
        this.roots = roots;
        this.maxDirectories = properties.maxDirectories();
    }

    List<LocalRoot> roots() {
        return roots.list();
    }

    // Same precedence as project creation (D-032): validation, root, path, then the disk.
    DirectoryListing list(String rootKey, @Nullable String rawPath) {
        String raw = rawPath == null ? "" : rawPath;
        if (RelativePaths.hasForbiddenCharacters(raw)) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldViolation("path", "validation.project.relative-path-chars")));
        }
        LocalRoot root =
                roots.find(rootKey).orElseThrow(() -> new ApiException(ErrorCode.ROOT_NOT_FOUND));
        String path = RelativePaths.normalize(raw);
        ConfinedPaths.Resolution resolution = ConfinedPaths.resolveDirectory(root.path(), path);
        Path directory =
                switch (resolution.status()) {
                    case FOUND -> Objects.requireNonNull(resolution.directory());
                    case NOT_READABLE -> throw new ApiException(ErrorCode.DIRECTORY_NOT_READABLE);
                    // Links are invisible, so they look like any missing directory.
                    case NOT_FOUND, NOT_A_DIRECTORY, SYMLINK ->
                            throw new ApiException(ErrorCode.DIRECTORY_NOT_FOUND);
                };
        List<String> names;
        try {
            names = subdirectoryNames(directory);
        } catch (IOException | DirectoryIteratorException e) {
            throw new ApiException(ErrorCode.DIRECTORY_NOT_READABLE);
        }
        names.sort(BY_NAME);
        List<DirectoryEntry> entries =
                names.stream()
                        .limit(maxDirectories)
                        .map(name -> entry(directory.resolve(name), path, name))
                        .toList();
        return new DirectoryListing(path, entries, names.size() > maxDirectories);
    }

    private static DirectoryEntry entry(Path directory, String parent, String name) {
        boolean readable = Files.isReadable(directory) && Files.isExecutable(directory);
        return new DirectoryEntry(
                name,
                parent.isEmpty() ? name : parent + "/" + name,
                readable && hasSubdirectory(directory),
                readable);
    }

    private static boolean hasSubdirectory(Path directory) {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                if (selectableName(child) != null) {
                    return true;
                }
            }
            return false;
        } catch (IOException | DirectoryIteratorException e) {
            return false;
        }
    }

    private static List<String> subdirectoryNames(Path directory) throws IOException {
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                String name = selectableName(child);
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    // Names a client could not send back as a path (backslash, control characters) are hidden.
    private static @Nullable String selectableName(Path child) {
        BasicFileAttributes attributes;
        try {
            attributes =
                    Files.readAttributes(
                            child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return null;
        }
        if (!attributes.isDirectory()) {
            return null;
        }
        String name = Normalizer.normalize(child.getFileName().toString(), Normalizer.Form.NFC);
        return RelativePaths.hasForbiddenCharacters(name) ? null : name;
    }

    record DirectoryListing(String path, List<DirectoryEntry> directories, boolean truncated) {}

    record DirectoryEntry(
            String name, String relativePath, boolean hasChildren, boolean readable) {}
}
