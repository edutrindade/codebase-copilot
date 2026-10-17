package com.codebasecopilot.retrieval;

import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.retrieval.RrfFusion.RankedHit;
import java.util.ArrayList;
import java.util.List;
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

    Retrieval(SearchService search) {
        this.search = search;
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

    public record Result(
            UUID snapshotId,
            SearchMode mode,
            @Nullable ModelInfo embeddingModel,
            @Nullable String lexicalVersion,
            List<RetrievedChunk> chunks) {

        public Result {
            chunks = List.copyOf(chunks);
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
        return new Result(
                result.snapshotId(),
                result.mode(),
                result.model(),
                result.lexicalVersion(),
                chunks);
    }
}
