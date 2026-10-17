package com.codebasecopilot.retrieval;

import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.retrieval.RrfFusion.RankedHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

// The search use case for other modules, in two steps: prepare runs every check (ApiException
// with the codes and precedence of the search, D-060) without calling a provider; retrieve
// embeds the question when the mode needs it and can only fail with an EMBEDDING_* code.
@Service
public class Retrieval {

    public static final int MAX_QUESTION_CHARS = SearchService.MAX_QUERY_CHARS;
    public static final int MAX_CANDIDATES = SearchService.MAX_LIMIT;

    private final SearchService search;
    private final SearchSnapshots snapshots;

    Retrieval(SearchService search, SearchSnapshots snapshots) {
        this.search = search;
        this.snapshots = snapshots;
    }

    public record Request(
            UUID projectId,
            String question,
            @Nullable UUID snapshotId,
            SearchMode mode,
            int candidates) {}

    public static final class Prepared {

        private final SearchService.Plan plan;

        private Prepared(SearchService.Plan plan) {
            this.plan = plan;
        }

        public UUID snapshotId() {
            return plan.snapshotId();
        }

        public SearchMode mode() {
            return plan.mode();
        }

        // Null when the mode has no vector channel.
        public @Nullable ModelInfo embeddingModel() {
            return plan.provider() == null ? null : plan.provider().model();
        }
    }

    // candidates: results of each channel before the fusion, by channel name.
    // embeddingMillis and embeddingTokens refer to the question; null without the vector
    // channel, and embeddingTokens also when the provider reports none (D-054).
    public record Result(
            UUID snapshotId,
            SearchMode mode,
            @Nullable ModelInfo embeddingModel,
            @Nullable String lexicalVersion,
            List<RetrievedChunk> chunks,
            Map<String, Integer> candidates,
            @Nullable Long embeddingMillis,
            @Nullable Long embeddingTokens) {

        public Result {
            chunks = List.copyOf(chunks);
            candidates = Map.copyOf(candidates);
        }
    }

    public Prepared prepare(Request request) {
        return new Prepared(
                search.prepare(
                        request.projectId(),
                        new SearchService.Query(
                                request.question(),
                                request.snapshotId(),
                                request.candidates(),
                                request.mode().name())));
    }

    public Result retrieve(Prepared prepared) {
        SearchService.Result result = search.run(prepared.plan);
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (RankedHit ranked : result.hits()) {
            SearchHit hit = ranked.hit();
            chunks.add(
                    new RetrievedChunk(
                            hit.chunkId(),
                            hit.paths(),
                            hit.contentHash(),
                            hit.startLine(),
                            hit.endLine(),
                            hit.content(),
                            hit.tokenEstimate(),
                            chunks.size() + 1,
                            ranked.ranks().lexical(),
                            ranked.ranks().vector(),
                            ranked.ranks().path()));
        }
        Map<String, Integer> candidates = new TreeMap<>();
        result.candidates().forEach((channel, count) -> candidates.put(channel.name(), count));
        return new Result(
                result.snapshotId(),
                result.mode(),
                result.model(),
                result.lexicalVersion(),
                chunks,
                candidates,
                result.embeddingMillis(),
                result.embeddingTokens());
    }

    // False when the snapshot was pruned or no longer exists: its files can no longer be opened,
    // only the copies kept with the answers (D-046).
    public boolean snapshotAvailable(UUID snapshotId) {
        return snapshots.find(snapshotId).map(state -> !state.pruned()).orElse(false);
    }
}
