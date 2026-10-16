package com.codebasecopilot.importjob.scan;

import com.codebasecopilot.project.Project;
import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.shared.datadir.DataDirectory;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.fs.ConfinedPaths;
import com.codebasecopilot.shared.localroots.LocalRoot;
import com.codebasecopilot.shared.localroots.LocalRoots;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

// The source tree of a project, shared by the inventory and the indexing (D-041). Paths never
// leave the backend.
@Service
public class ProjectSources {

    private final ProjectService projects;
    private final LocalRoots roots;
    private final DataDirectory dataDirectory;
    private final InventoryProperties properties;
    private final ProjectSettingsService settings;
    private final SafeScanner scanner = new SafeScanner();

    ProjectSources(
            ProjectService projects,
            LocalRoots roots,
            DataDirectory dataDirectory,
            InventoryProperties properties,
            ProjectSettingsService settings) {
        this.projects = projects;
        this.roots = roots;
        this.dataDirectory = dataDirectory;
        this.properties = properties;
        this.settings = settings;
    }

    public record SourceFile(String relativePath, Path path, long sizeBytes, String language) {}

    // files is sorted by relativePath; it is empty when the scan did not collect files.
    public record SourceScan(
            int settingsVersion,
            long maxFileBytes,
            long maxProjectBytes,
            ScanResult result,
            List<SourceFile> files) {}

    // Same checks and order as the inventory: PROJECT_NOT_FOUND, then the SOURCE_* errors.
    public Path resolve(UUID projectId) {
        return resolve(projects.get(projectId).project());
    }

    public SourceScan scan(UUID projectId) {
        return scan(projectId, () -> {});
    }

    // onFile runs for every included file, so long scans can report liveness.
    public SourceScan scan(UUID projectId, Runnable onFile) {
        Project project = projects.get(projectId).project();
        return scan(project, resolve(project), true, onFile);
    }

    public int settingsVersion(UUID projectId) {
        return settings.effective(projectId).settings().version();
    }

    Path resolve(Project project) {
        return switch (project.sourceType()) {
            case LOCAL_FOLDER -> {
                LocalRoot root =
                        roots.find(Objects.requireNonNull(project.sourceRootKey()))
                                .orElseThrow(
                                        () ->
                                                new ApiException(
                                                        ErrorCode.SOURCE_ROOT_NOT_CONFIGURED));
                yield confined(root.path(), Objects.requireNonNull(project.sourceRelativePath()));
            }
            case ZIP -> {
                UUID current = project.currentImportJobId();
                if (current == null) {
                    throw new ApiException(ErrorCode.SOURCE_NOT_IMPORTED);
                }
                yield confined(dataDirectory.importSourceDir(current), "");
            }
        };
    }

    SourceScan scan(Project project, Path source, boolean collectFiles) {
        return scan(project, source, collectFiles, () -> {});
    }

    private SourceScan scan(Project project, Path source, boolean collectFiles, Runnable onFile) {
        ProjectSettingsService.Effective effective = settings.effective(project.id());
        List<SourceFile> files = new ArrayList<>();
        SafeScanner.Listener listener =
                collectFiles
                        ? (relativePath, file, sizeBytes, language) -> {
                            files.add(new SourceFile(relativePath, file, sizeBytes, language));
                            onFile.run();
                        }
                        : (relativePath, file, sizeBytes, language) -> {};
        ScanResult result;
        try {
            result =
                    scanner.scan(
                            source,
                            new SafeScanner.Rules(effective.rules(), effective.patterns()),
                            new SafeScanner.Limits(
                                    properties.maxEntries(),
                                    properties.maxDepth(),
                                    effective.maxFileBytes()),
                            listener);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.SOURCE_NOT_READABLE);
        }
        files.sort(Comparator.comparing(SourceFile::relativePath));
        return new SourceScan(
                effective.settings().version(),
                effective.maxFileBytes(),
                effective.maxProjectBytes(),
                result,
                List.copyOf(files));
    }

    private static Path confined(Path base, String relativePath) {
        ConfinedPaths.Resolution resolution = ConfinedPaths.resolveDirectory(base, relativePath);
        return switch (resolution.status()) {
            case FOUND -> Objects.requireNonNull(resolution.directory());
            case NOT_FOUND, NOT_A_DIRECTORY ->
                    throw new ApiException(ErrorCode.SOURCE_DIRECTORY_NOT_FOUND);
            case SYMLINK -> throw new ApiException(ErrorCode.SOURCE_IS_SYMLINK);
            case NOT_READABLE -> throw new ApiException(ErrorCode.SOURCE_NOT_READABLE);
        };
    }
}
