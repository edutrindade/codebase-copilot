package com.codebasecopilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codebasecopilot.project.Project;
import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

// Without a provider (app.embeddings.provider=none); the API tests run with the fake.
class SearchServiceTest {

    private final UUID projectId = UUID.randomUUID();
    private final UUID snapshotId = UUID.randomUUID();
    private final ProjectService projects = mock(ProjectService.class);
    private final VectorSearch search = mock(VectorSearch.class);
    private final SearchService service =
            new SearchService(
                    projects,
                    search,
                    new StaticListableBeanFactory().getBeanProvider(EmbeddingProvider.class));

    @Test
    void searchWithoutProviderIsNotAvailable() {
        activeSnapshot(snapshotId);
        when(search.findSnapshot(snapshotId))
                .thenReturn(
                        Optional.of(
                                new VectorSearch.SnapshotState(
                                        projectId, "READY", false, new ModelInfo("fake", "m", 8))));

        assertCode(
                new SearchService.Query("alpha", null, null), ErrorCode.EMBEDDINGS_NOT_AVAILABLE);
    }

    @Test
    void missingActiveSnapshotComesBeforeTheProviderCheck() {
        activeSnapshot(null);

        assertCode(new SearchService.Query("alpha", null, null), ErrorCode.SNAPSHOT_NOT_SEARCHABLE);
    }

    @Test
    void validationComesBeforeAnyLookup() {
        assertCode(new SearchService.Query(" ", null, 0), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(projects, search);
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
