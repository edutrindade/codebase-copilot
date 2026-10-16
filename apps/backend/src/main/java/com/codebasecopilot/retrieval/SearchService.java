package com.codebasecopilot.retrieval;

import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.providers.EmbeddingFailure;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.error.FieldViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

// Checks follow the precedence of D-053. The query is sent to the provider but never logged
// (D-054); provider calls are not retried, since a person is waiting (D-051).
@Service
class SearchService {

    static final int MAX_QUERY_CHARS = 2000;
    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;

    private static final Set<String> SEARCHABLE = Set.of("READY", "PARTIAL");

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final ProjectService projects;
    private final VectorSearch search;
    private final @Nullable EmbeddingProvider provider;

    SearchService(
            ProjectService projects,
            VectorSearch search,
            ObjectProvider<EmbeddingProvider> provider) {
        this.projects = projects;
        this.search = search;
        this.provider = provider.getIfAvailable();
    }

    record Query(@Nullable String text, @Nullable UUID snapshotId, @Nullable Integer limit) {}

    record Result(UUID snapshotId, ModelInfo model, List<SearchHit> hits) {}

    Result search(UUID projectId, Query query) {
        String text = query.text();
        int limit = query.limit() == null ? DEFAULT_LIMIT : query.limit();
        validate(text, limit);
        UUID snapshotId = snapshotId(projectId, query.snapshotId());
        VectorSearch.SnapshotState snapshot = searchable(projectId, snapshotId);
        EmbeddingProvider provider = this.provider;
        if (provider == null) {
            throw new ApiException(ErrorCode.EMBEDDINGS_NOT_AVAILABLE);
        }
        ModelInfo model = provider.model();
        if (!model.equals(snapshot.embedding())) {
            throw new ApiException(ErrorCode.SNAPSHOT_EMBEDDING_MISMATCH);
        }
        long start = System.nanoTime();
        float[] vector = embed(provider, text);
        List<SearchHit> hits = search.search(snapshotId, model, vector, limit);
        log.info(
                "Vector search on snapshot {} returned {} results in {} ms",
                snapshotId,
                hits.size(),
                (System.nanoTime() - start) / 1_000_000);
        return new Result(snapshotId, model, hits);
    }

    private static void validate(@Nullable String text, int limit) {
        List<FieldViolation> violations = new ArrayList<>();
        if (text == null || text.isBlank()) {
            violations.add(new FieldViolation("query", "validation.search.query-required"));
        } else if (text.length() > MAX_QUERY_CHARS) {
            violations.add(new FieldViolation("query", "validation.search.query-too-long"));
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            violations.add(new FieldViolation("limit", "validation.search.limit-out-of-range"));
        }
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, violations);
        }
    }

    // An explicit snapshot of another project is reported as not found, like a missing one.
    private UUID snapshotId(UUID projectId, @Nullable UUID requested) {
        UUID active = projects.find(projectId).activeSnapshotId();
        if (requested == null) {
            if (active == null) {
                throw new ApiException(ErrorCode.SNAPSHOT_NOT_SEARCHABLE);
            }
            return active;
        }
        return requested;
    }

    private VectorSearch.SnapshotState searchable(UUID projectId, UUID snapshotId) {
        VectorSearch.SnapshotState snapshot =
                search.findSnapshot(snapshotId)
                        .filter(s -> s.projectId().equals(projectId))
                        .orElseThrow(() -> new ApiException(ErrorCode.SNAPSHOT_NOT_FOUND));
        if (!SEARCHABLE.contains(snapshot.status()) || snapshot.pruned()) {
            throw new ApiException(ErrorCode.SNAPSHOT_NOT_SEARCHABLE);
        }
        return snapshot;
    }

    private static float[] embed(EmbeddingProvider provider, String text) {
        try {
            return provider.embed(List.of(text)).vectors().getFirst();
        } catch (EmbeddingFailure e) {
            throw new ApiException(code(e.type()));
        }
    }

    static ErrorCode code(EmbeddingFailure.Type type) {
        return switch (type) {
            case UNAUTHORIZED -> ErrorCode.EMBEDDING_PROVIDER_UNAUTHORIZED;
            case RATE_LIMITED -> ErrorCode.EMBEDDING_RATE_LIMITED;
            case QUOTA_EXCEEDED -> ErrorCode.EMBEDDING_QUOTA_EXCEEDED;
            case UNAVAILABLE -> ErrorCode.EMBEDDING_PROVIDER_UNAVAILABLE;
            case INVALID_REQUEST -> ErrorCode.EMBEDDING_REQUEST_REJECTED;
        };
    }
}
