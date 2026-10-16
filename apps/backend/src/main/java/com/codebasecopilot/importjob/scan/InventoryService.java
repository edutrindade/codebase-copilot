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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

// Pre-analysis computed on demand and never persisted (D-035); it reads files only as bytes.
@Service
@EnableConfigurationProperties(InventoryProperties.class)
class InventoryService {

    private final ProjectService projects;
    private final LocalRoots roots;
    private final DataDirectory dataDirectory;
    private final InventoryProperties properties;
    private final ProjectSettingsService settings;
    private final Clock clock;
    private final SafeScanner scanner = new SafeScanner();

    InventoryService(
            ProjectService projects,
            LocalRoots roots,
            DataDirectory dataDirectory,
            InventoryProperties properties,
            ProjectSettingsService settings,
            Clock clock) {
        this.projects = projects;
        this.roots = roots;
        this.dataDirectory = dataDirectory;
        this.properties = properties;
        this.settings = settings;
        this.clock = clock;
    }

    Inventory inventory(UUID projectId) {
        Project project = projects.get(projectId).project();
        Path source = source(project);
        ProjectSettingsService.Effective effective = settings.effective(projectId);
        ScanResult scan;
        try {
            scan =
                    scanner.scan(
                            source,
                            new SafeScanner.Rules(effective.rules(), effective.patterns()),
                            new SafeScanner.Limits(
                                    properties.maxEntries(),
                                    properties.maxDepth(),
                                    effective.maxFileBytes()));
        } catch (IOException e) {
            throw new ApiException(ErrorCode.SOURCE_NOT_READABLE);
        }
        return new Inventory(
                projectId,
                clock.instant(),
                effective.settings().version(),
                scan,
                warnings(project, source, scan, effective.maxProjectBytes()));
    }

    private Path source(Project project) {
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

    private List<Inventory.Warning> warnings(
            Project project, Path source, ScanResult scan, long maxProjectBytes) {
        List<Inventory.Warning> warnings = new ArrayList<>();
        List<UUID> sameFolder = sameFolderProjects(project, source);
        if (!sameFolder.isEmpty()) {
            warnings.add(new Inventory.Warning(InventoryWarning.CASE_VARIANT_PROJECT, sameFolder));
        }
        if (scan.truncated()) {
            warnings.add(new Inventory.Warning(InventoryWarning.SCAN_LIMIT_REACHED, List.of()));
        }
        if (scan.totals().includedBytes() > maxProjectBytes) {
            warnings.add(
                    new Inventory.Warning(InventoryWarning.PROJECT_SIZE_LIMIT_EXCEEDED, List.of()));
        }
        if (scan.totals().includedFiles() == 0) {
            warnings.add(new Inventory.Warning(InventoryWarning.NO_INCLUDED_FILES, List.of()));
        }
        return warnings;
    }

    private List<UUID> sameFolderProjects(Project project, Path source) {
        List<UUID> ids = new ArrayList<>();
        for (Project other : projects.findCaseVariants(project)) {
            Optional<Path> otherSource =
                    roots.find(Objects.requireNonNull(other.sourceRootKey()))
                            .map(
                                    root ->
                                            ConfinedPaths.resolveDirectory(
                                                            root.path(),
                                                            Objects.requireNonNull(
                                                                    other.sourceRelativePath()))
                                                    .directory());
            if (otherSource.isPresent() && isSameFile(source, otherSource.get())) {
                ids.add(other.id());
            }
        }
        return ids;
    }

    private static boolean isSameFile(Path a, Path b) {
        try {
            return Files.isSameFile(a, b);
        } catch (IOException e) {
            return false;
        }
    }
}
