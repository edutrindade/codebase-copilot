package com.codebasecopilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import com.codebasecopilot.project.Project;
import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

// Without a provider (app.embeddings.provider=none); the API tests run with the fake.
class SearchServiceTest {

    private static final ModelInfo MODEL = new ModelInfo("fake", "m", 8);

    private final UUID projectId = UUID.randomUUID();
    private final UUID snapshotId = UUID.randomUUID();
    private final ProjectService projects = mock(ProjectService.class);
    private final SearchSnapshots snapshots = mock(SearchSnapshots.class);
    private final LexicalSearch lexical = mock(LexicalSearch.class);
    private final VectorSearch vector = mock(VectorSearch.class);
    private final PathSearch path = mock(PathSearch.class);
    private final SearchService service =
            new SearchService(
                    projects,
                    snapshots,
                    lexical,
                    vector,
                    path,
                    new RetrievalProperties(50),
                    new StaticListableBeanFactory().getBeanProvider(EmbeddingProvider.class));

    @ParameterizedTest
    @CsvSource({"VECTOR", "HYBRID"})
    void vectorModesWithoutProviderAreNotAvailable(String mode) {
        snapshot(IdentifierTokenizer.VERSION);

        assertCode(query(mode), ErrorCode.EMBEDDINGS_NOT_AVAILABLE);
        verifyNoInteractions(lexical, vector, path);
    }

    @Test
    void defaultModeIsHybrid() {
        snapshot(IdentifierTokenizer.VERSION);

        assertCode(query(null), ErrorCode.EMBEDDINGS_NOT_AVAILABLE);
    }

    @Test
    void lexicalModeWorksWithoutProvider() {
        snapshot(IdentifierTokenizer.VERSION);
        SearchHit hit = new SearchHit(UUID.randomUUID(), List.of("a.java"), 1, 2, "alpha", 0.3);
        when(lexical.search(snapshotId, "alpha", 10)).thenReturn(List.of(hit));

        SearchService.Result result = service.search(projectId, query("LEXICAL"));

        assertThat(result.mode()).isEqualTo(SearchMode.LEXICAL);
        assertThat(result.model()).isNull();
        assertThat(result.lexicalVersion()).isEqualTo(IdentifierTokenizer.VERSION);
        assertThat(result.hits())
                .singleElement()
                .satisfies(
                        r -> {
                            assertThat(r.hit()).isEqualTo(hit);
                            assertThat(r.score()).isEqualTo(0.3);
                            assertThat(r.ranks()).isEqualTo(new RrfFusion.Ranks(1, null, null));
                        });
        verifyNoInteractions(vector, path);
    }

    @ParameterizedTest
    @CsvSource({"LEXICAL", "HYBRID"})
    void lexicalMismatchComesBeforeTheProviderCheck(String mode) {
        snapshot(null);

        assertCode(query(mode), ErrorCode.SNAPSHOT_LEXICAL_MISMATCH);
    }

    @Test
    void vectorModeIgnoresTheLexicalVersion() {
        snapshot("ident-v0");

        assertCode(query("VECTOR"), ErrorCode.EMBEDDINGS_NOT_AVAILABLE);
    }

    @Test
    void missingActiveSnapshotComesBeforeTheProviderCheck() {
        activeSnapshot(null);

        assertCode(query("HYBRID"), ErrorCode.SNAPSHOT_NOT_SEARCHABLE);
    }

    @Test
    void validationComesBeforeAnyLookup() {
        assertCode(new SearchService.Query(" ", null, 0, "lexical"), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(projects, snapshots, lexical, vector, path);
    }

    @Test
    void lexicalModeFetchesOnlyTheLimit() {
        snapshot(IdentifierTokenizer.VERSION);
        when(lexical.search(snapshotId, "alpha", 3)).thenReturn(List.of());

        service.search(projectId, new SearchService.Query("alpha", null, 3, "LEXICAL"));

        verify(lexical).search(snapshotId, "alpha", 3);
    }

    private SearchService.Query query(String mode) {
        return new SearchService.Query("alpha", null, null, mode);
    }

    private void snapshot(String lexicalVersion) {
        activeSnapshot(snapshotId);
        when(snapshots.find(snapshotId))
                .thenReturn(
                        Optional.of(
                                new SearchSnapshots.SnapshotState(
                                        projectId, "READY", false, MODEL, lexicalVersion)));
    }

    private void activeSnapshot(UUID id) {
        Project project = mock(Project.class);
        when(project.activeSnapshotId()).thenReturn(id);
        when(projects.find(projectId)).thenReturn(project);
    }

    private void assertCode(SearchService.Query query, ErrorCode code) {
        assertThatThrownBy(() -> service.search(projectId, query))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
