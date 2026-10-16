package com.codebasecopilot.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// Reciprocal rank fusion (D-017, D-059): score = Σ weight / (k + rank) over the channels that
// returned the chunk. Pure, so the evaluation can vary k and the weights over lists fetched once.
final class RrfFusion {

    enum Channel {
        LEXICAL,
        VECTOR,
        PATH
    }

    record Parameters(int k, Map<Channel, Double> weights) {

        static final Parameters DEFAULT = new Parameters(60, Map.of());

        Parameters {
            if (k < 1) {
                throw new IllegalArgumentException("k must be positive");
            }
            weights.values()
                    .forEach(
                            weight -> {
                                if (!(weight >= 0)) {
                                    throw new IllegalArgumentException(
                                            "weights must not be negative");
                                }
                            });
            weights = Map.copyOf(weights);
        }

        double weight(Channel channel) {
            return weights.getOrDefault(channel, 1.0);
        }
    }

    // 1-based position of the chunk in each channel's list; null when the channel missed it.
    record Ranks(@Nullable Integer lexical, @Nullable Integer vector, @Nullable Integer path) {

        static final Ranks NONE = new Ranks(null, null, null);

        Ranks with(Channel channel, int rank) {
            return switch (channel) {
                case LEXICAL -> new Ranks(rank, vector, path);
                case VECTOR -> new Ranks(lexical, rank, path);
                case PATH -> new Ranks(lexical, vector, rank);
            };
        }

        int best() {
            int best = Integer.MAX_VALUE;
            for (Integer rank : new Integer[] {lexical, vector, path}) {
                if (rank != null) {
                    best = Math.min(best, rank);
                }
            }
            return best;
        }
    }

    record RankedHit(SearchHit hit, double score, Ranks ranks) {}

    // Ties: higher score, better individual rank, first path, start line, chunk id (as text, the
    // order of the database).
    private static final Comparator<RankedHit> ORDER =
            Comparator.comparingDouble(RankedHit::score)
                    .reversed()
                    .thenComparingInt(ranked -> ranked.ranks().best())
                    .thenComparing(ranked -> ranked.hit().paths().getFirst())
                    .thenComparingInt(ranked -> ranked.hit().startLine())
                    .thenComparing(ranked -> ranked.hit().chunkId().toString());

    private RrfFusion() {}

    // A channel with an empty list adds nothing.
    static List<RankedHit> fuse(
            Map<Channel, List<SearchHit>> lists, Parameters parameters, int limit) {
        Map<UUID, Accumulator> byChunk = new LinkedHashMap<>();
        for (Channel channel : Channel.values()) {
            List<SearchHit> hits = lists.getOrDefault(channel, List.of());
            for (int i = 0; i < hits.size(); i++) {
                SearchHit hit = hits.get(i);
                byChunk.computeIfAbsent(hit.chunkId(), id -> new Accumulator(hit))
                        .add(channel, i + 1, parameters);
            }
        }
        List<RankedHit> fused = new ArrayList<>();
        byChunk.values().forEach(acc -> fused.add(acc.toRankedHit()));
        fused.sort(ORDER);
        return List.copyOf(fused.subList(0, Math.min(limit, fused.size())));
    }

    // One channel alone keeps its own order and score.
    static List<RankedHit> single(Channel channel, List<SearchHit> hits) {
        List<RankedHit> ranked = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            SearchHit hit = hits.get(i);
            ranked.add(new RankedHit(hit, hit.score(), Ranks.NONE.with(channel, i + 1)));
        }
        return List.copyOf(ranked);
    }

    private static final class Accumulator {

        private final SearchHit hit;
        private double score;
        private Ranks ranks = Ranks.NONE;

        Accumulator(SearchHit hit) {
            this.hit = hit;
        }

        void add(Channel channel, int rank, Parameters parameters) {
            score += parameters.weight(channel) / (parameters.k() + rank);
            ranks = ranks.with(channel, rank);
        }

        RankedHit toRankedHit() {
            return new RankedHit(hit, score, ranks);
        }
    }
}
