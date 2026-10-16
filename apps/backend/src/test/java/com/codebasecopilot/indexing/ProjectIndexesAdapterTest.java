package com.codebasecopilot.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.project.Project;
import com.codebasecopilot.project.SourceType;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ProjectIndexesAdapterTest {

    private static final UUID IMPORT = UUID.randomUUID();

    @Test
    void noSnapshotIsNeverOutdated() {
        assertThat(ProjectIndexesAdapter.outdatedReasons(zip(UUID.randomUUID()), null, 5))
                .isEmpty();
    }

    @Test
    void snapshotMatchingTheProjectIsUpToDate() {
        assertThat(
                        ProjectIndexesAdapter.outdatedReasons(
                                zip(IMPORT), snapshot(2, IMPORT, LineChunker.VERSION), 2))
                .isEmpty();
    }

    @Test
    void reportsEveryReason() {
        assertThat(
                        ProjectIndexesAdapter.outdatedReasons(
                                zip(UUID.randomUUID()), snapshot(1, IMPORT, "lines-v0"), 2))
                .containsExactly("SETTINGS_CHANGED", "SOURCE_REIMPORTED", "CHUNKING_CHANGED");
    }

    @Test
    void localFolderIsNeverReimported() {
        Project local =
                new Project(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "L",
                        SourceType.LOCAL_FOLDER,
                        "default",
                        "",
                        null,
                        null,
                        Instant.EPOCH,
                        Instant.EPOCH);

        assertThat(
                        ProjectIndexesAdapter.outdatedReasons(
                                local, snapshot(0, null, LineChunker.VERSION), 0))
                .isEmpty();
    }

    private static Project zip(UUID currentImport) {
        return new Project(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Z",
                SourceType.ZIP,
                null,
                null,
                currentImport,
                null,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static Snapshot snapshot(int settingsVersion, @Nullable UUID importId, String version) {
        return new Snapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SnapshotStatus.READY,
                importId,
                settingsVersion,
                version,
                null,
                new SnapshotRepository.Counts(0, 0, 0, 0, 0, 0),
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
    }
}
