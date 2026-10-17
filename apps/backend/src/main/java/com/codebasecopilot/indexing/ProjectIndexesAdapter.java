package com.codebasecopilot.indexing;

import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import com.codebasecopilot.importjob.scan.ProjectSettingsVersions;
import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.project.Project;
import com.codebasecopilot.project.ProjectIndexes;
import com.codebasecopilot.project.SourceType;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.ModelInfo;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

// Active snapshot, running job and outdated reasons of a page of projects, in three batch
// queries (D-044).
@Component
class ProjectIndexesAdapter implements ProjectIndexes {

    private final SnapshotRepository snapshots;
    private final IndexJobRepository jobs;
    private final ProjectSettingsVersions settingsVersions;
    private final @Nullable EmbeddingProvider provider;
    private final LineChunker.Parameters chunking;

    ProjectIndexesAdapter(
            SnapshotRepository snapshots,
            IndexJobRepository jobs,
            ProjectSettingsVersions settingsVersions,
            ObjectProvider<EmbeddingProvider> provider,
            LineChunker.Parameters chunking) {
        this.snapshots = snapshots;
        this.jobs = jobs;
        this.settingsVersions = settingsVersions;
        this.provider = provider.getIfAvailable();
        this.chunking = chunking;
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
        ModelInfo model = provider == null ? null : provider.model();
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
                                    project,
                                    snapshot,
                                    versions.getOrDefault(project.id(), 0),
                                    chunking.version(),
                                    model)));
        }
        return states;
    }

    // Without a configured model nothing is embedded, so no snapshot lacks vectors (D-050).
    static List<String> outdatedReasons(
            Project project,
            @Nullable Snapshot snapshot,
            int settingsVersion,
            String chunkingVersion,
            @Nullable ModelInfo model) {
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
        if (!snapshot.chunkingVersion().equals(chunkingVersion)) {
            reasons.add("CHUNKING_CHANGED");
        }
        if (snapshot.lexicalVersion() == null) {
            reasons.add("LEXICAL_INDEX_MISSING");
        } else if (!snapshot.lexicalVersion().equals(IdentifierTokenizer.VERSION)) {
            reasons.add("LEXICAL_INDEX_CHANGED");
        }
        SnapshotRepository.Embedding embedding = snapshot.embedding();
        if (model != null) {
            if (embedding == null) {
                reasons.add("EMBEDDINGS_MISSING");
            } else if (!embedding.model().equals(model)) {
                reasons.add("EMBEDDING_MODEL_CHANGED");
            }
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
