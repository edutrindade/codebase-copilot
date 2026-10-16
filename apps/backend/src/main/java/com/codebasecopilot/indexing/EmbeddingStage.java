package com.codebasecopilot.indexing;

import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.providers.EmbeddingFailure;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.EmbeddingResult;
import com.codebasecopilot.providers.EmbeddingRetry;
import com.codebasecopilot.providers.InputLimits;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.providers.ProviderProperties;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Embeds the chunks of the snapshot that have no vector of the configured model yet (D-050).
// Only chunk texts are sent; logs carry counts only (D-054).
@Component
class EmbeddingStage {

    record Result(ModelInfo model, @Nullable Long tokens) {}

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStage.class);

    private final @Nullable EmbeddingProvider provider;
    private final EmbeddingRetry retry;
    private final ChunkEmbeddingRepository embeddings;
    private final TransactionTemplate transactions;
    private final ProviderProperties providerProperties;
    private final IndexingProperties properties;
    private final Clock clock;

    EmbeddingStage(
            ObjectProvider<EmbeddingProvider> provider,
            EmbeddingRetry retry,
            ChunkEmbeddingRepository embeddings,
            TransactionTemplate transactions,
            ProviderProperties providerProperties,
            IndexingProperties properties,
            Clock clock) {
        this.provider = provider.getIfAvailable();
        this.retry = retry;
        this.embeddings = embeddings;
        this.transactions = transactions;
        this.providerProperties = providerProperties;
        this.properties = properties;
        this.clock = clock;
        if (this.provider != null) {
            checkLimits(this.provider.limits(), providerProperties.batchSize());
        }
    }

    // Counts one token per character: code averages several characters per token, so a chunk
    // stays below the limit; one that does not is rejected by the provider (D-054).
    static void checkLimits(InputLimits limits, int batchSize) {
        if (LineChunker.MAX_CHARS > limits.maxInputTokens()) {
            throw new IllegalStateException(
                    "app.embeddings.model accepts %d tokens per input, below the %d characters of a chunk"
                            .formatted(limits.maxInputTokens(), LineChunker.MAX_CHARS));
        }
        int maxBatch =
                Math.min(
                        limits.maxInputsPerRequest(),
                        limits.maxTokensPerRequest() / LineChunker.MAX_CHARS);
        if (batchSize > maxBatch) {
            throw new IllegalStateException(
                    "app.embeddings.batch-size must be at most %d for the configured model"
                            .formatted(maxBatch));
        }
    }

    // Null when no provider is configured: the snapshot is activated without vectors.
    @Nullable Result run(Claim claim, Heartbeat heartbeat) {
        EmbeddingProvider provider = this.provider;
        if (provider == null) {
            return null;
        }
        ModelInfo model = provider.model();
        List<String> missing = embeddings.missingChunkHashes(claim.snapshotId(), model);
        // Checked before any call, so an oversized job costs nothing (D-054).
        if (missing.size() > providerProperties.maxChunksPerJob()) {
            throw new IndexFailure(IndexErrorCode.EMBEDDING_CHUNK_LIMIT_EXCEEDED);
        }
        heartbeat.stage(IndexStage.EMBEDDING, missing.size(), 0);
        int batchSize = providerProperties.batchSize();
        long start = System.nanoTime();
        long tokens = 0;
        boolean tokensReported = true;
        int batches = 0;
        for (int from = 0; from < missing.size(); from += batchSize) {
            List<String> hashes = missing.subList(from, Math.min(from + batchSize, missing.size()));
            Map<String, String> texts = embeddings.chunkTexts(claim.projectId(), hashes);
            List<String> inputs =
                    hashes.stream().map(hash -> Objects.requireNonNull(texts.get(hash))).toList();
            EmbeddingResult result = embed(provider, inputs, heartbeat);
            List<ChunkEmbeddingRepository.NewEmbedding> rows = new ArrayList<>(hashes.size());
            for (int i = 0; i < hashes.size(); i++) {
                rows.add(
                        new ChunkEmbeddingRepository.NewEmbedding(
                                hashes.get(i), result.vectors().get(i)));
            }
            int completed = from + hashes.size();
            transactions.executeWithoutResult(
                    tx -> {
                        heartbeat.progress(completed);
                        embeddings.insert(
                                model, rows, clock.instant().truncatedTo(ChronoUnit.MICROS));
                    });
            if (result.tokens() == null) {
                tokensReported = false;
            } else {
                tokens += result.tokens();
            }
            batches++;
        }
        log.info(
                "Index job {} embedded {} chunks in {} batches ({} tokens) in {} ms",
                claim.jobId(),
                missing.size(),
                batches,
                tokensReported ? tokens : "unreported",
                (System.nanoTime() - start) / 1_000_000);
        // A partial count would be an invented number (D-054).
        return new Result(model, tokensReported ? tokens : null);
    }

    // Waits between retries renew the lease, so another worker does not take the job (D-051).
    private EmbeddingResult embed(
            EmbeddingProvider provider, List<String> inputs, Heartbeat heartbeat) {
        try {
            return retry.call(
                    () -> provider.embed(inputs), properties.heartbeatInterval(), heartbeat::renew);
        } catch (EmbeddingFailure e) {
            throw new IndexFailure(code(e.type()));
        }
    }

    static IndexErrorCode code(EmbeddingFailure.Type type) {
        return switch (type) {
            case UNAUTHORIZED -> IndexErrorCode.EMBEDDING_PROVIDER_UNAUTHORIZED;
            case RATE_LIMITED -> IndexErrorCode.EMBEDDING_RATE_LIMITED;
            case QUOTA_EXCEEDED -> IndexErrorCode.EMBEDDING_QUOTA_EXCEEDED;
            case UNAVAILABLE -> IndexErrorCode.EMBEDDING_PROVIDER_UNAVAILABLE;
            case INVALID_REQUEST -> IndexErrorCode.EMBEDDING_REQUEST_REJECTED;
        };
    }
}
