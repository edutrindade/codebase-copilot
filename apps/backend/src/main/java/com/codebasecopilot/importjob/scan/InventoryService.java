package com.codebasecopilot.importjob.scan;

import com.codebasecopilot.project.Project;
import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.shared.fs.ConfinedPaths;
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
    private final ProjectSources sources;
    private final Clock clock;

    InventoryService(
            ProjectService projects, LocalRoots roots, ProjectSources sources, Clock clock) {
        this.projects = projects;
        this.roots = roots;
        this.sources = sources;
        this.clock = clock;
    }

    Inventory inventory(UUID projectId) {
        Project project = projects.get(projectId).project();
        Path source = sources.resolve(project);
        ProjectSources.SourceScan scan = sources.scan(project, source, false);
        return new Inventory(
                projectId,
                clock.instant(),
                scan.settingsVersion(),
                scan.result(),
                warnings(project, source, scan.result(), scan.maxProjectBytes()));
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
