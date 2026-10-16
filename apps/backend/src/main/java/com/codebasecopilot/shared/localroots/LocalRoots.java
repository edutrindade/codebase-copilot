package com.codebasecopilot.shared.localroots;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class LocalRoots {

    static final String DEFAULT_KEY = "default";
    static final String DEFAULT_LABEL = "Projetos";

    private static final Pattern KEY = Pattern.compile("[a-z0-9-]+");

    private final Map<String, LocalRoot> roots;

    private LocalRoots(Map<String, LocalRoot> roots) {
        this.roots = roots;
    }

    /**
     * Validates the configured roots (D-029). {@code app.local-roots}, when present, replaces the
     * {@code PROJECTS_ROOT} shortcut entirely; an empty shortcut creates no root.
     *
     * @throws IllegalStateException if any root is invalid, so the application does not start
     */
    public static LocalRoots from(LocalRootsProperties properties) {
        Map<String, LocalRoot> roots = new LinkedHashMap<>();
        for (Source source : sources(properties)) {
            LocalRoot root = validate(source);
            if (roots.putIfAbsent(root.key(), root) != null) {
                throw invalid(source, "duplicate key '" + root.key() + "'");
            }
        }
        return new LocalRoots(roots);
    }

    public boolean isEmpty() {
        return roots.isEmpty();
    }

    public Optional<LocalRoot> find(String key) {
        return Optional.ofNullable(roots.get(key));
    }

    public List<LocalRoot> list() {
        return List.copyOf(roots.values());
    }

    private static List<Source> sources(LocalRootsProperties properties) {
        List<Source> sources = new ArrayList<>();
        if (!properties.localRoots().isEmpty()) {
            for (int i = 0; i < properties.localRoots().size(); i++) {
                LocalRootsProperties.Entry entry = properties.localRoots().get(i);
                sources.add(
                        new Source(
                                "app.local-roots[" + i + "]",
                                entry.key(),
                                entry.label(),
                                entry.path()));
            }
        } else if (!isBlank(properties.projectsRoot())) {
            String label =
                    isBlank(properties.projectsRootLabel())
                            ? DEFAULT_LABEL
                            : properties.projectsRootLabel();
            sources.add(new Source("PROJECTS_ROOT", DEFAULT_KEY, label, properties.projectsRoot()));
        }
        return sources;
    }

    private static LocalRoot validate(Source source) {
        String key = source.key() == null ? "" : source.key().strip();
        if (!KEY.matcher(key).matches()) {
            throw invalid(source, "key must match [a-z0-9-]+");
        }
        if (isBlank(source.path())) {
            throw invalid(source, "path is required");
        }
        Path path;
        try {
            path = Path.of(source.path().strip());
        } catch (InvalidPathException e) {
            throw invalid(source, "path is not a valid path");
        }
        if (!path.isAbsolute()) {
            throw invalid(source, "path must be absolute");
        }
        if (!Files.isDirectory(path)) {
            throw invalid(source, "path must be an existing directory");
        }
        if (!Files.isReadable(path)) {
            throw invalid(source, "path must be readable");
        }
        String label = isBlank(source.label()) ? key : source.label().strip();
        return new LocalRoot(key, label, path.normalize());
    }

    private static IllegalStateException invalid(Source source, String reason) {
        return new IllegalStateException(
                "Invalid local root " + source.origin() + ": " + reason + " (D-029)");
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private record Source(
            String origin, @Nullable String key, @Nullable String label, @Nullable String path) {}
}
