package com.codebasecopilot.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.providers.EmbeddingFailure;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.EmbeddingRetry;
import com.codebasecopilot.providers.InputLimits;
import com.codebasecopilot.providers.ProviderProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionTemplate;

class EmbeddingStageTest {

    private static final InputLimits OPENAI = new InputLimits(8192, 2048, 300_000);

    @Test
    void defaultBatchFitsTheOpenAiLimits() {
        assertThatCode(() -> EmbeddingStage.checkLimits(OPENAI, 64)).doesNotThrowAnyException();
        assertThatCode(() -> EmbeddingStage.checkLimits(OPENAI, 75)).doesNotThrowAnyException();
    }

    @Test
    void batchAboveTheTokensPerRequestIsRefused() {
        assertThatThrownBy(() -> EmbeddingStage.checkLimits(OPENAI, 76))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "app.embeddings.batch-size must be at most 75 for the configured model");
    }

    @Test
    void batchAboveTheInputsPerRequestIsRefused() {
        InputLimits limits = new InputLimits(8192, 10, Integer.MAX_VALUE);

        assertThatThrownBy(() -> EmbeddingStage.checkLimits(limits, 11))
                .hasMessage(
                        "app.embeddings.batch-size must be at most 10 for the configured model");
    }

    @Test
    void modelAcceptingLessThanAChunkIsRefused() {
        InputLimits limits = new InputLimits(LineChunker.MAX_CHARS - 1, 2048, 300_000);

        assertThatThrownBy(() -> EmbeddingStage.checkLimits(limits, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.embeddings.model accepts 3999 tokens per input");
    }

    @Test
    void unboundedLimitsAcceptAnyBatch() {
        assertThatCode(() -> EmbeddingStage.checkLimits(InputLimits.UNBOUNDED, 100_000))
                .doesNotThrowAnyException();
    }

    @Test
    void stageIsSkippedWithoutProvider() {
        ChunkEmbeddingRepository embeddings = mock(ChunkEmbeddingRepository.class);
        Heartbeat heartbeat = mock(Heartbeat.class);
        ObjectProvider<EmbeddingProvider> none =
                new StaticListableBeanFactory().getBeanProvider(EmbeddingProvider.class);
        EmbeddingStage stage =
                new EmbeddingStage(
                        none,
                        new EmbeddingRetry(
                                0, Duration.ofSeconds(1), Duration.ofSeconds(1), d -> {}),
                        embeddings,
                        mock(TransactionTemplate.class),
                        new ProviderProperties(
                                ProviderProperties.Provider.NONE,
                                "text-embedding-3-small",
                                1536,
                                64,
                                4,
                                Duration.ofSeconds(60),
                                50_000,
                                Duration.ofSeconds(30),
                                URI.create(ProviderProperties.OFFICIAL_BASE_URL),
                                null),
                        new IndexingProperties(
                                1,
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(10),
                                Duration.ofSeconds(60),
                                3,
                                100,
                                500,
                                3),
                        Clock.systemUTC());
        Claim claim = new Claim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);

        assertThat(stage.run(claim, heartbeat)).isNull();
        verifyNoInteractions(embeddings, heartbeat);
    }

    @Test
    void failuresMapToTheirJobCodes() {
        assertThat(EmbeddingStage.code(EmbeddingFailure.Type.UNAUTHORIZED))
                .isEqualTo(IndexErrorCode.EMBEDDING_PROVIDER_UNAUTHORIZED);
        assertThat(EmbeddingStage.code(EmbeddingFailure.Type.RATE_LIMITED))
                .isEqualTo(IndexErrorCode.EMBEDDING_RATE_LIMITED);
        assertThat(EmbeddingStage.code(EmbeddingFailure.Type.QUOTA_EXCEEDED))
                .isEqualTo(IndexErrorCode.EMBEDDING_QUOTA_EXCEEDED);
        assertThat(EmbeddingStage.code(EmbeddingFailure.Type.UNAVAILABLE))
                .isEqualTo(IndexErrorCode.EMBEDDING_PROVIDER_UNAVAILABLE);
        assertThat(EmbeddingStage.code(EmbeddingFailure.Type.INVALID_REQUEST))
                .isEqualTo(IndexErrorCode.EMBEDDING_REQUEST_REJECTED);
    }
}
