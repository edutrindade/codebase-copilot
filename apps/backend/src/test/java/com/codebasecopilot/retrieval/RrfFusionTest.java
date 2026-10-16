package com.codebasecopilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import com.codebasecopilot.retrieval.RrfFusion.Channel;
import com.codebasecopilot.retrieval.RrfFusion.Parameters;
import com.codebasecopilot.retrieval.RrfFusion.RankedHit;
import com.codebasecopilot.retrieval.RrfFusion.Ranks;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RrfFusionTest {

    private static final SearchHit A = hit("00000000-0000-0000-0000-00000000000a", "a.java", 1);
    private static final SearchHit B = hit("00000000-0000-0000-0000-00000000000b", "b.java", 1);
    private static final SearchHit C = hit("00000000-0000-0000-0000-00000000000c", "c.java", 1);
    private static final SearchHit D = hit("00000000-0000-0000-0000-00000000000d", "d.java", 1);

    @Test
    void sumsTheReciprocalRanksOfEveryChannel() {
        List<RankedHit> fused =
                RrfFusion.fuse(
                        Map.of(Channel.LEXICAL, List.of(A, B, C), Channel.VECTOR, List.of(C, A)),
                        Parameters.DEFAULT,
                        10);

        assertThat(fused).extracting(r -> r.hit().chunkId()).containsExactly(id(A), id(C), id(B));
        assertThat(fused.get(0).score()).isCloseTo(1.0 / 61 + 1.0 / 62, offset(1e-12));
        assertThat(fused.get(1).score()).isCloseTo(1.0 / 63 + 1.0 / 61, offset(1e-12));
        assertThat(fused.get(2).score()).isCloseTo(1.0 / 62, offset(1e-12));
        assertThat(fused.get(0).ranks()).isEqualTo(new Ranks(1, 2, null));
        assertThat(fused.get(1).ranks()).isEqualTo(new Ranks(3, 1, null));
        assertThat(fused.get(2).ranks()).isEqualTo(new Ranks(2, null, null));
    }

    @Test
    void itemInOneChannelKeepsOnlyThatRank() {
        List<RankedHit> fused =
                RrfFusion.fuse(
                        Map.of(
                                Channel.LEXICAL, List.of(A),
                                Channel.VECTOR, List.of(B),
                                Channel.PATH, List.of(C)),
                        Parameters.DEFAULT,
                        10);

        assertThat(fused)
                .extracting(RankedHit::ranks)
                .containsExactly(
                        new Ranks(1, null, null),
                        new Ranks(null, 1, null),
                        new Ranks(null, null, 1));
    }

    @Test
    void equalScoresAreOrderedByBestRank() {
        // With k=1: 2/(1+1) = 3/(1+2) = 1, exactly.
        SearchHit firstInLexical = hit("00000000-0000-0000-0000-000000000001", "z.java", 1);
        SearchHit secondInPath = hit("00000000-0000-0000-0000-000000000002", "a.java", 1);
        List<RankedHit> fused =
                RrfFusion.fuse(
                        Map.of(
                                Channel.LEXICAL, List.of(firstInLexical),
                                Channel.PATH, List.of(C, secondInPath)),
                        new Parameters(1, Map.of(Channel.LEXICAL, 2.0, Channel.PATH, 3.0)),
                        10);

        assertThat(fused)
                .extracting(r -> r.hit().chunkId())
                .containsExactly(id(C), id(firstInLexical), id(secondInPath));
        assertThat(fused.get(1).score()).isEqualTo(fused.get(2).score());
    }

    @Test
    void equalScoresAndRanksAreOrderedByPathLineAndId() {
        SearchHit laterLine = hit("00000000-0000-0000-0000-000000000001", "same.java", 9);
        SearchHit higherId = hit("00000000-0000-0000-0000-0000000000f0", "same.java", 3);
        SearchHit lowerId = hit("00000000-0000-0000-0000-0000000000e0", "same.java", 3);
        List<RankedHit> fused =
                RrfFusion.fuse(
                        Map.of(
                                Channel.LEXICAL, List.of(laterLine),
                                Channel.VECTOR, List.of(higherId),
                                Channel.PATH, List.of(lowerId)),
                        Parameters.DEFAULT,
                        10);

        assertThat(fused)
                .extracting(r -> r.hit().chunkId())
                .containsExactly(id(lowerId), id(higherId), id(laterLine));
    }

    @Test
    void weightsAndKChangeTheScores() {
        List<RankedHit> fused =
                RrfFusion.fuse(
                        Map.of(Channel.LEXICAL, List.of(A), Channel.VECTOR, List.of(B)),
                        new Parameters(1, Map.of(Channel.VECTOR, 3.0)),
                        10);

        assertThat(fused).extracting(r -> r.hit().chunkId()).containsExactly(id(B), id(A));
        assertThat(fused.get(0).score()).isCloseTo(1.5, offset(1e-12));
        assertThat(fused.get(1).score()).isCloseTo(0.5, offset(1e-12));
    }

    @Test
    void emptyListsAddNothingAndLimitBoundsTheResult() {
        assertThat(RrfFusion.fuse(Map.of(Channel.LEXICAL, List.of()), Parameters.DEFAULT, 10))
                .isEmpty();
        assertThat(RrfFusion.fuse(Map.of(), Parameters.DEFAULT, 10)).isEmpty();

        List<RankedHit> withEmptyPath =
                RrfFusion.fuse(
                        Map.of(Channel.LEXICAL, List.of(A, B, C), Channel.PATH, List.of()),
                        Parameters.DEFAULT,
                        2);

        assertThat(withEmptyPath).extracting(r -> r.hit().chunkId()).containsExactly(id(A), id(B));
        assertThat(withEmptyPath).allSatisfy(r -> assertThat(r.ranks().path()).isNull());
    }

    @Test
    void singleChannelKeepsItsOrderAndScore() {
        SearchHit scored = new SearchHit(id(A), List.of("a.java"), 1, 2, "a", 0.75);

        List<RankedHit> ranked = RrfFusion.single(Channel.VECTOR, List.of(scored, B));

        assertThat(ranked).extracting(RankedHit::score).containsExactly(0.75, B.score());
        assertThat(ranked)
                .extracting(RankedHit::ranks)
                .containsExactly(new Ranks(null, 1, null), new Ranks(null, 2, null));
    }

    @Test
    void sameListsGiveTheSameOrder() {
        Map<Channel, List<SearchHit>> lists =
                Map.of(Channel.LEXICAL, List.of(D, C, B, A), Channel.VECTOR, List.of(A, B, C, D));

        assertThat(RrfFusion.fuse(lists, Parameters.DEFAULT, 10))
                .isEqualTo(RrfFusion.fuse(lists, Parameters.DEFAULT, 10));
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new Parameters(0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Parameters(60, Map.of(Channel.PATH, -1.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SearchHit hit(String id, String path, int startLine) {
        return new SearchHit(UUID.fromString(id), List.of(path), startLine, startLine, path, 0.5);
    }

    private static UUID id(SearchHit hit) {
        return hit.chunkId();
    }
}
