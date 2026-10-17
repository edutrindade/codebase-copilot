package com.codebasecopilot.retrieval;

import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.providers.EmbeddingFailure;
import com.codebasecopilot.providers.EmbeddingProvider;
import com.codebasecopilot.providers.EmbeddingResult;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.retrieval.RrfFusion.Channel;
import com.codebasecopilot.retrieval.RrfFusion.RankedHit;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.error.FieldViolation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

// Checks follow the precedence of D-060. The query is sent to the provider but never logged
// (D-054); provider calls are not retried, since a person is waiting (D-051).
@Service
@EnableConfigurationProperties(RetrievalProperties.class)
class SearchService {

    static final int MAX_QUERY_CHARS = 2000;
    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;
    static final SearchMode DEFAULT_MODE = SearchMode.HYBRID;

    private static final Set<String> SEARCHABLE = Set.of("READY", "PARTIAL");

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final ProjectService projects;
    private final SearchSnapshots snapshots;
    private final LexicalSearch lexical;
    private final VectorSearch vector;
    private final PathSearch path;
    private final RetrievalProperties properties;
    private final @Nullable EmbeddingProvider provider;

    SearchService(
            ProjectService projects,
            SearchSnapshots snapshots,
            LexicalSearch lexical,
            VectorSearch vector,
            PathSearch path,
            RetrievalProperties properties,
            ObjectProvider<EmbeddingProvider> provider) {
        this.projects = projects;
        this.snapshots = snapshots;
        this.lexical = lexical;
        this.vector = vector;
        this.path = path;
        this.properties = properties;
        this.provider = provider.getIfAvailable();
    }

    record Query(
            @Nullable String text,
            @Nullable UUID snapshotId,
            @Nullable Integer limit,
            @Nullable String mode) {}

    // model, embeddingMillis and embeddingTokens are null without the vector channel,
    // lexicalVersion without the lexical one; embeddingTokens also when the provider reports none.
    record Result(
            UUID snapshotId,
            SearchMode mode,
            @Nullable ModelInfo model,
            @Nullable String lexicalVersion,
            List<RankedHit> hits,
            Map<Channel, Integer> candidates,
            @Nullable Long embeddingMillis,
            @Nullable Long embeddingTokens) {}

    // A query that passed every check; provider is null when the mode has no vector channel.
    record Plan(
            String text,
            UUID snapshotId,
            SearchMode mode,
            int limit,
            @Nullable EmbeddingProvider provider) {}

    Result search(UUID projectId, Query query) {
        return run(prepare(projectId, query));
    }

    // Every check that does not call a provider, so answers can fail before persisting anything.
    Plan prepare(UUID projectId, Query query) {
        String text = query.text();
        int limit = query.limit() == null ? DEFAULT_LIMIT : query.limit();
        SearchMode mode = validate(text, limit, query.mode());
        UUID snapshotId = snapshotId(projectId, query.snapshotId());
        SearchSnapshots.SnapshotState snapshot = searchable(projectId, snapshotId);
        if (mode.usesLexical() && !IdentifierTokenizer.VERSION.equals(snapshot.lexicalVersion())) {
            throw new ApiException(ErrorCode.SNAPSHOT_LEXICAL_MISMATCH);
        }
        EmbeddingProvider provider = mode.usesVector() ? provider(snapshot) : null;
        return new Plan(Objects.requireNonNull(text), snapshotId, mode, limit, provider);
    }

    // Only embedding failures of the question are left: an ApiException with an EMBEDDING_* code.
    Result run(Plan plan) {
        String text = plan.text();
        UUID snapshotId = plan.snapshotId();
        SearchMode mode = plan.mode();
        int limit = plan.limit();
        EmbeddingProvider provider = plan.provider();

        Map<Channel, List<SearchHit>> lists = new EnumMap<>(Channel.class);
        Map<Channel, Long> millis = new EnumMap<>(Channel.class);
        int depth = mode == SearchMode.HYBRID ? Math.max(properties.candidates(), limit) : limit;
        Long embeddingMillis = null;
        Long embeddingTokens = null;
        if (provider != null) {
            ModelInfo model = provider.model();
            long start = System.nanoTime();
            EmbeddingResult embedded = embed(provider, text);
            embeddingMillis = (System.nanoTime() - start) / 1_000_000;
            embeddingTokens = embedded.tokens();
            float[] queryVector = embedded.vectors().getFirst();
            timed(
                    Channel.VECTOR,
                    lists,
                    millis,
                    () -> vector.search(snapshotId, model, queryVector, depth));
        }
        if (mode.usesLexical()) {
            timed(Channel.LEXICAL, lists, millis, () -> lexical.search(snapshotId, text, depth));
        }
        if (mode == SearchMode.HYBRID) {
            timed(
                    Channel.PATH,
                    lists,
                    millis,
                    () -> path.search(snapshotId, text, PathSearch.DEFAULT_THRESHOLD, depth));
        }

        List<RankedHit> hits =
                switch (mode) {
                    case LEXICAL -> RrfFusion.single(Channel.LEXICAL, lists.get(Channel.LEXICAL));
                    case VECTOR -> RrfFusion.single(Channel.VECTOR, lists.get(Channel.VECTOR));
                    case HYBRID -> RrfFusion.fuse(lists, RrfFusion.Parameters.DEFAULT, limit);
                };
        Map<Channel, Integer> sizes = sizes(lists);
        log.info(
                "Search in {} mode on snapshot {} returned {} results (per channel: {} results, {}"
                        + " ms; question embedding: {} ms)",
                mode,
                snapshotId,
                hits.size(),
                sizes,
                millis,
                embeddingMillis);
        return new Result(
                snapshotId,
                mode,
                provider == null ? null : provider.model(),
                mode.usesLexical() ? IdentifierTokenizer.VERSION : null,
                hits,
                sizes,
                embeddingMillis,
                embeddingTokens);
    }

    private static SearchMode validate(@Nullable String text, int limit, @Nullable String mode) {
        List<FieldViolation> violations = new ArrayList<>();
        if (text == null || text.isBlank()) {
            violations.add(new FieldViolation("query", "validation.search.query-required"));
        } else if (text.length() > MAX_QUERY_CHARS) {
            violations.add(new FieldViolation("query", "validation.search.query-too-long"));
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            violations.add(new FieldViolation("limit", "validation.search.limit-out-of-range"));
        }
        SearchMode parsed = mode(mode);
        if (parsed == null) {
            violations.add(new FieldViolation("mode", "validation.search.mode-invalid"));
        }
        if (!violations.isEmpty() || parsed == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, violations);
        }
        return parsed;
    }

    // Exact names only, so the accepted values are the ones in the contract.
    private static @Nullable SearchMode mode(@Nullable String mode) {
        if (mode == null) {
            return DEFAULT_MODE;
        }
        for (SearchMode candidate : SearchMode.values()) {
            if (candidate.name().equals(mode)) {
                return candidate;
            }
        }
        return null;
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

    private SearchSnapshots.SnapshotState searchable(UUID projectId, UUID snapshotId) {
        SearchSnapshots.SnapshotState snapshot =
                snapshots
                        .find(snapshotId)
                        .filter(s -> s.projectId().equals(projectId))
                        .orElseThrow(() -> new ApiException(ErrorCode.SNAPSHOT_NOT_FOUND));
        if (!SEARCHABLE.contains(snapshot.status()) || snapshot.pruned()) {
            throw new ApiException(ErrorCode.SNAPSHOT_NOT_SEARCHABLE);
        }
        return snapshot;
    }

    private EmbeddingProvider provider(SearchSnapshots.SnapshotState snapshot) {
        EmbeddingProvider provider = this.provider;
        if (provider == null) {
            throw new ApiException(ErrorCode.EMBEDDINGS_NOT_AVAILABLE);
        }
        if (!provider.model().equals(snapshot.embedding())) {
            throw new ApiException(ErrorCode.SNAPSHOT_EMBEDDING_MISMATCH);
        }
        return provider;
    }

    private static void timed(
            Channel channel,
            Map<Channel, List<SearchHit>> lists,
            Map<Channel, Long> millis,
            Supplier<List<SearchHit>> search) {
        long start = System.nanoTime();
        lists.put(channel, search.get());
        millis.put(channel, (System.nanoTime() - start) / 1_000_000);
    }

    private static Map<Channel, Integer> sizes(Map<Channel, List<SearchHit>> lists) {
        Map<Channel, Integer> sizes = new EnumMap<>(Channel.class);
        lists.forEach((channel, hits) -> sizes.put(channel, hits.size()));
        return sizes;
    }

    private static EmbeddingResult embed(EmbeddingProvider provider, String text) {
        try {
            return provider.embed(List.of(text));
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
