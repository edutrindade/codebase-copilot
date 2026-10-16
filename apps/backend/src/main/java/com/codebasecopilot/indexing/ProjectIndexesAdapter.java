package com.codebasecopilot.indexing;

import com.codebasecopilot.importjob.scan.ProjectSettingsVersions;
import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.project.Project;
import com.codebasecopilot.project.ProjectIndexes;
import com.codebasecopilot.project.SourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

// Active snapshot, running job and outdated reasons of a page of projects, in three batch
// queries (D-044).
@Component
class ProjectIndexesAdapter implements ProjectIndexes {

    private final SnapshotRepository snapshots;
    private final IndexJobRepository jobs;
    private final ProjectSettingsVersions settingsVersions;

    ProjectIndexesAdapter(
            SnapshotRepository snapshots,
            IndexJobRepository jobs,
            ProjectSettingsVersions settingsVersions) {
        this.snapshots = snapshots;
        this.jobs = jobs;
        this.settingsVersions = settingsVersions;
    }

    @Override
    public Map<UUID, IndexState> find(Collection<Project> projects) {
        List<UUID> projectIds = projects.stream().map(Project::id).toList();
        Map<UUID, Snapshot> active =
                snapshots
                        .findByIds(
                                projects.stream()
                                        .map(Project::activeSnapshotId)
                                        .filter(Objects::nonNull)
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(Snapshot::id, Function.identity()));
        Map<UUID, IndexJob> running =
                jobs.findActiveByProjects(projectIds).stream()
                        .collect(Collectors.toMap(IndexJob::projectId, Function.identity()));
        Map<UUID, Integer> versions = settingsVersions.find(projectIds);
        Map<UUID, IndexState> states = new HashMap<>();
        for (Project project : projects) {
            Snapshot snapshot =
                    project.activeSnapshotId() == null
                            ? null
                            : active.get(project.activeSnapshotId());
            IndexJob job = running.get(project.id());
            states.put(
                    project.id(),
                    new IndexState(
                            snapshot == null ? null : activeSnapshot(snapshot),
                            job == null ? null : indexing(job),
                            outdatedReasons(
                                    project, snapshot, versions.getOrDefault(project.id(), 0))));
        }
        return states;
    }

    static List<String> outdatedReasons(
            Project project, @Nullable Snapshot snapshot, int settingsVersion) {
        List<String> reasons = new ArrayList<>();
        if (snapshot == null) {
            return reasons;
        }
        if (snapshot.settingsVersion() != settingsVersion) {
            reasons.add("SETTINGS_CHANGED");
        }
        if (project.sourceType() == SourceType.ZIP
                && !Objects.equals(project.currentImportJobId(), snapshot.sourceImportJobId())) {
            reasons.add("SOURCE_REIMPORTED");
        }
        if (!snapshot.chunkingVersion().equals(LineChunker.VERSION)) {
            reasons.add("CHUNKING_CHANGED");
        }
        return reasons;
    }

    private static ActiveSnapshot activeSnapshot(Snapshot snapshot) {
        SnapshotRepository.Counts c = Objects.requireNonNull(snapshot.counts());
        return new ActiveSnapshot(
                snapshot.id(),
                snapshot.status().name(),
                snapshot.completedAt(),
                new Counts(
                        c.files(),
                        c.indexedFiles(),
                        c.unsupportedFiles(),
                        c.failedFiles(),
                        c.chunks(),
                        c.bytes()));
    }

    private static Indexing indexing(IndexJob job) {
        return new Indexing(
                job.id(),
                job.status().name(),
                job.stage() == null ? null : job.stage().name(),
                job.progressTotal(),
                job.progressCompleted());
    }
}
