package com.codebasecopilot.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.project.Project;
import com.codebasecopilot.project.SourceType;
import com.codebasecopilot.providers.ModelInfo;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ProjectIndexesAdapterTest {

    private static final UUID IMPORT = UUID.randomUUID();
    private static final ModelInfo MODEL = new ModelInfo("fake", "fake-v1", 8);

    @Test
    void noSnapshotIsNeverOutdated() {
        assertThat(ProjectIndexesAdapter.outdatedReasons(zip(UUID.randomUUID()), null, 5, null))
                .isEmpty();
    }

    @Test
    void snapshotMatchingTheProjectIsUpToDate() {
        assertThat(
                        ProjectIndexesAdapter.outdatedReasons(
                                zip(IMPORT), snapshot(2, IMPORT, LineChunker.VERSION), 2, null))
                .isEmpty();
    }

    @Test
    void reportsEveryReason() {
        assertThat(
                        ProjectIndexesAdapter.outdatedReasons(
                                zip(UUID.randomUUID()), snapshot(1, IMPORT, "lines-v0"), 2, null))
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
                                local, snapshot(0, null, LineChunker.VERSION), 0, null))
                .isEmpty();
    }

    @Test
    void snapshotWithVectorsOfTheConfiguredModelIsUpToDate() {
        assertThat(ProjectIndexesAdapter.outdatedReasons(zip(IMPORT), snapshot(MODEL), 0, MODEL))
                .isEmpty();
    }

    @Test
    void anotherConfiguredModelIsAModelChange() {
        for (ModelInfo configured :
                new ModelInfo[] {
                    new ModelInfo("fake", "fake-v2", 8),
                    new ModelInfo("fake", "fake-v1", 16),
                    new ModelInfo("openai", "fake-v1", 8)
                }) {
            assertThat(
                            ProjectIndexesAdapter.outdatedReasons(
                                    zip(IMPORT), snapshot(MODEL), 0, configured))
                    .containsExactly("EMBEDDING_MODEL_CHANGED");
        }
    }

    @Test
    void snapshotWithoutVectorsIsMissingThemOnlyWhenAProviderIsConfigured() {
        Snapshot withoutVectors = snapshot(0, IMPORT, LineChunker.VERSION);

        assertThat(ProjectIndexesAdapter.outdatedReasons(zip(IMPORT), withoutVectors, 0, MODEL))
                .containsExactly("EMBEDDINGS_MISSING");
        assertThat(ProjectIndexesAdapter.outdatedReasons(zip(IMPORT), withoutVectors, 0, null))
                .isEmpty();
    }

    @Test
    void vectorsAreIgnoredWhenNoProviderIsConfigured() {
        assertThat(ProjectIndexesAdapter.outdatedReasons(zip(IMPORT), snapshot(MODEL), 0, null))
                .isEmpty();
    }

    @Test
    void snapshotWithoutLexicalIndexIsMissingIt() {
        assertThat(ProjectIndexesAdapter.outdatedReasons(zip(IMPORT), lexical(null), 0, null))
                .containsExactly("LEXICAL_INDEX_MISSING");
    }

    @Test
    void anotherTokenizerVersionIsALexicalChange() {
        assertThat(ProjectIndexesAdapter.outdatedReasons(zip(IMPORT), lexical("ident-v0"), 0, null))
                .containsExactly("LEXICAL_INDEX_CHANGED");
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

    private static Snapshot snapshot(ModelInfo model) {
        return snapshot(0, IMPORT, LineChunker.VERSION, model, IdentifierTokenizer.VERSION);
    }

    private static Snapshot snapshot(int settingsVersion, @Nullable UUID importId, String version) {
        return snapshot(settingsVersion, importId, version, null, IdentifierTokenizer.VERSION);
    }

    private static Snapshot lexical(@Nullable String lexicalVersion) {
        return snapshot(0, IMPORT, LineChunker.VERSION, null, lexicalVersion);
    }

    private static Snapshot snapshot(
            int settingsVersion,
            @Nullable UUID importId,
            String version,
            @Nullable ModelInfo model,
            @Nullable String lexicalVersion) {
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
                model == null ? null : new SnapshotRepository.Embedding(model, 3L),
                lexicalVersion,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
    }
}
