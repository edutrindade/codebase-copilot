package com.codebasecopilot.shared.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import org.jspecify.annotations.Nullable;

// Symbolic links are never followed, not even when they point inside the base (D-018).
public final class ConfinedPaths {

    public enum Status {
        FOUND,
        NOT_FOUND,
        NOT_A_DIRECTORY,
        SYMLINK,
        NOT_READABLE
    }

    /** {@code directory} is the real path and is present only when the status is FOUND. */
    public record Resolution(Status status, @Nullable Path directory) {

        static Resolution of(Status status) {
            return new Resolution(status, null);
        }
    }

    private ConfinedPaths() {}

    /**
     * Resolves a directory below {@code base}, checking every segment without following links.
     *
     * @param relativePath already normalized by {@link RelativePaths#normalize}; {@code ""} is the
     *     base itself
     */
    public static Resolution resolveDirectory(Path base, String relativePath) {
        Path realBase;
        try {
            realBase = base.toRealPath();
        } catch (IOException e) {
            return Resolution.of(Status.NOT_FOUND);
        }
        Path current = realBase;
        if (!relativePath.isEmpty()) {
            for (String segment : relativePath.split("/")) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    throw new IllegalArgumentException("relative path is not normalized");
                }
                current = current.resolve(segment);
                Status status = checkSegment(current);
                if (status != Status.FOUND) {
                    return Resolution.of(status);
                }
            }
        }
        if (!Files.isReadable(current)) {
            return Resolution.of(Status.NOT_READABLE);
        }
        try {
            // Defense in depth: no segment was a link, so this only fails on a concurrent swap.
            if (!current.toRealPath().startsWith(realBase)) {
                return Resolution.of(Status.SYMLINK);
            }
        } catch (IOException e) {
            return Resolution.of(Status.NOT_FOUND);
        }
        return new Resolution(Status.FOUND, current);
    }

    private static Status checkSegment(Path path) {
        BasicFileAttributes attributes;
        try {
            attributes =
                    Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            return Status.NOT_FOUND;
        } catch (IOException e) {
            return Status.NOT_READABLE;
        }
        if (attributes.isSymbolicLink()) {
            return Status.SYMLINK;
        }
        if (!attributes.isDirectory()) {
            return Status.NOT_A_DIRECTORY;
        }
        return Status.FOUND;
    }
}
