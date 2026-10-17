package com.codebasecopilot.answering;

import static com.codebasecopilot.answering.Chunks.chunk;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.answering.ContextAssembler.Limits;
import com.codebasecopilot.retrieval.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextAssemblerTest {

    private static final Limits WIDE = new Limits(8, 100_000, 3);

    @Test
    void labelsTheChosenChunksInFusionOrder() {
        RetrievedChunk a = chunk("a.java", 1, 10, 1);
        RetrievedChunk b = chunk("b.java", 1, 10, 2);
        RetrievedChunk c = chunk("c.java", 1, 10, 3);

        List<ContextEvidence> evidence = ContextAssembler.assemble("q", List.of(a, b, c), WIDE);

        assertThat(evidence).extracting(ContextEvidence::label).containsExactly("E1", "E2", "E3");
        assertThat(evidence).extracting(ContextEvidence::chunk).containsExactly(a, b, c);
    }

    @Test
    void stopsAtMaxEvidence() {
        List<RetrievedChunk> chunks =
                List.of(
                        chunk("a.java", 1, 5, 1),
                        chunk("b.java", 1, 5, 2),
                        chunk("c.java", 1, 5, 3));

        List<ContextEvidence> evidence =
                ContextAssembler.assemble("q", chunks, new Limits(2, 100_000, 3));

        assertThat(evidence).extracting(ContextEvidence::path).containsExactly("a.java", "b.java");
    }

    @Test
    void limitsTheEvidencePerFileToLeaveRoomForOthers() {
        List<RetrievedChunk> chunks =
                List.of(
                        chunk("a.java", 1, 10, 1),
                        chunk("a.java", 21, 30, 2),
                        chunk("a.java", 41, 50, 3),
                        chunk("b.java", 1, 10, 4));

        List<ContextEvidence> evidence =
                ContextAssembler.assemble("q", chunks, new Limits(8, 100_000, 2));

        assertThat(evidence)
                .extracting(e -> e.path() + ":" + e.chunk().startLine())
                .containsExactly("a.java:1", "a.java:21", "b.java:1");
    }

    @Test
    void skipsAChunkThatOverlapsAChosenOneOfTheSameContent() {
        List<RetrievedChunk> chunks =
                List.of(
                        chunk("a.java", 1, 60, 1),
                        chunk("a.java", 51, 110, 2),
                        chunk("b.java", 51, 110, 3),
                        chunk("a.java", 61, 120, 4));

        List<ContextEvidence> evidence = ContextAssembler.assemble("q", chunks, WIDE);

        assertThat(evidence)
                .extracting(e -> e.label() + "=" + e.path() + ":" + e.chunk().startLine())
                .containsExactly("E1=a.java:1", "E2=b.java:51", "E3=a.java:61");
    }

    @Test
    void keepsWithinTheTokenBudgetAndTriesTheNextChunks() {
        RetrievedChunk small = chunk("a.java", 1, 2, 1);
        RetrievedChunk large = chunk("b.java", 1, 400, 2);
        RetrievedChunk alsoSmall = chunk("c.java", 1, 2, 3);
        int budget = fixedCost("q") + cost("E1", small) + cost("E2", alsoSmall);

        List<ContextEvidence> evidence =
                ContextAssembler.assemble(
                        "q", List.of(small, large, alsoSmall), new Limits(8, budget, 3));

        assertThat(evidence).extracting(ContextEvidence::chunk).containsExactly(small, alsoSmall);
        assertThat(evidence).extracting(ContextEvidence::label).containsExactly("E1", "E2");
    }

    @Test
    void reservesTheInstructionsAndTheQuestion() {
        RetrievedChunk only = chunk("a.java", 1, 2, 1);
        int budget = fixedCost("short") + cost("E1", only);
        String longQuestion = "why ".repeat(100);

        assertThat(ContextAssembler.assemble("short", List.of(only), new Limits(8, budget, 3)))
                .hasSize(1);
        assertThat(ContextAssembler.assemble(longQuestion, List.of(only), new Limits(8, budget, 3)))
                .isEmpty();
    }

    @Test
    void nothingRetrievedMeansNoEvidence() {
        assertThat(ContextAssembler.assemble("q", List.of(), WIDE)).isEmpty();
    }

    @Test
    void estimateRoundsUpAQuarterOfTheCharacters() {
        assertThat(ContextAssembler.estimate("")).isZero();
        assertThat(ContextAssembler.estimate("a")).isEqualTo(1);
        assertThat(ContextAssembler.estimate("abcd")).isEqualTo(1);
        assertThat(ContextAssembler.estimate("abcde")).isEqualTo(2);
    }

    private static int fixedCost(String question) {
        String nonce = PromptBuilder.placeholderNonce();
        return ContextAssembler.estimate(PromptBuilder.instructions(nonce))
                + ContextAssembler.estimate(PromptBuilder.questionBlock(question, nonce));
    }

    private static int cost(String label, RetrievedChunk chunk) {
        return ContextAssembler.estimate(
                        PromptBuilder.evidenceBlock(
                                new ContextEvidence(label, chunk),
                                PromptBuilder.placeholderNonce()))
                + 1;
    }
}
