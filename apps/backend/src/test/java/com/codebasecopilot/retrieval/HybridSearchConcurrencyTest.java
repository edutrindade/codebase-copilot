package com.codebasecopilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import com.codebasecopilot.project.Project;
import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.providers.EmbeddingFailure;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.EmbeddingResult;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.retrieval.RrfFusion.Channel;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

// HYBRID runs the lexical and path channels while the question is embedded and the vector channel
// runs (F2 of Etapa 8).
class HybridSearchConcurrencyTest {

    private static final ModelInfo MODEL = new ModelInfo("fake", "m", 2);

    private final UUID projectId = UUID.randomUUID();
    private final UUID snapshotId = UUID.randomUUID();
    private final ProjectService projects = mock(ProjectService.class);
    private final SearchSnapshots snapshots = mock(SearchSnapshots.class);
    private final LexicalSearch lexical = mock(LexicalSearch.class);
    private final VectorSearch vector = mock(VectorSearch.class);
    private final PathSearch path = mock(PathSearch.class);
    private final EmbeddingProvider provider = mock(EmbeddingProvider.class);
    private final SearchHit lexicalHit = hit("a.java");
    private final SearchHit vectorHit = hit("b.java");
    private SearchService service;

    @BeforeEach
    void setUp() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("provider", provider);
        service =
                new SearchService(
                        projects,
                        snapshots,
                        lexical,
                        vector,
                        path,
                        new RetrievalProperties(50),
                        beans.getBeanProvider(EmbeddingProvider.class));
        Project project = mock(Project.class);
        when(project.activeSnapshotId()).thenReturn(snapshotId);
        when(projects.find(projectId)).thenReturn(project);
        when(snapshots.find(snapshotId))
                .thenReturn(
                        Optional.of(
                                new SearchSnapshots.SnapshotState(
                                        projectId,
                                        "READY",
                                        false,
                                        MODEL,
                                        IdentifierTokenizer.VERSION)));
        when(provider.model()).thenReturn(MODEL);
        when(provider.embed(List.of("alpha")))
                .thenReturn(new EmbeddingResult(List.of(new float[] {1, 0}), 3L));
        when(path.search(eq(snapshotId), eq("alpha"), anyDouble(), anyInt())).thenReturn(List.of());
    }

    @Test
    void lexicalChannelRunsWhileTheVectorChannelWaits() {
        CountDownLatch lexicalStarted = new CountDownLatch(1);
        AtomicBoolean overlapped = new AtomicBoolean();
        when(lexical.search(snapshotId, "alpha", 50))
                .thenAnswer(
                        invocation -> {
                            lexicalStarted.countDown();
                            return List.of(lexicalHit);
                        });
        // Run one after the other, the vector channel would time out waiting for the lexical one.
        when(vector.search(eq(snapshotId), eq(MODEL), any(float[].class), eq(50)))
                .thenAnswer(
                        invocation -> {
                            overlapped.set(lexicalStarted.await(5, TimeUnit.SECONDS));
                            return List.of(vectorHit);
                        });

        SearchService.Result result = service.search(projectId, query());

        assertThat(overlapped).isTrue();
        assertThat(result.hits())
                .extracting(r -> r.hit().paths().getFirst())
                .contains("a.java", "b.java");
        assertThat(result.candidates())
                .containsEntry(Channel.LEXICAL, 1)
                .containsEntry(Channel.VECTOR, 1)
                .containsEntry(Channel.PATH, 0);
        assertThat(result.embeddingTokens()).isEqualTo(3L);
    }

    @Test
    void failureOfAParallelChannelReachesTheCallerUnwrapped() {
        IllegalStateException failure = new IllegalStateException("lexical failed");
        when(lexical.search(snapshotId, "alpha", 50)).thenThrow(failure);
        when(vector.search(eq(snapshotId), eq(MODEL), any(float[].class), eq(50)))
                .thenReturn(List.of(vectorHit));

        assertThatThrownBy(() -> service.search(projectId, query())).isSameAs(failure);
    }

    @Test
    void embeddingFailureWaitsForTheOtherChannels() {
        AtomicBoolean lexicalFinished = new AtomicBoolean();
        when(lexical.search(snapshotId, "alpha", 50))
                .thenAnswer(
                        invocation -> {
                            Thread.sleep(100);
                            lexicalFinished.set(true);
                            return List.of(lexicalHit);
                        });
        when(provider.embed(List.of("alpha")))
                .thenThrow(new EmbeddingFailure(EmbeddingFailure.Type.UNAVAILABLE, "down"));

        assertThatThrownBy(() -> service.search(projectId, query()))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e ->
                                assertThat(e.code())
                                        .isEqualTo(ErrorCode.EMBEDDING_PROVIDER_UNAVAILABLE));
        assertThat(lexicalFinished).isTrue();
    }

    private SearchService.Query query() {
        return new SearchService.Query("alpha", null, null, "HYBRID");
    }

    private static SearchHit hit(String path) {
        return new SearchHit(UUID.randomUUID(), List.of(path), "h", 1, 2, "alpha", 2, 0.3);
    }
}
