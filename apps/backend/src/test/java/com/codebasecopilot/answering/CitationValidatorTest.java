package com.codebasecopilot.answering;

import static com.codebasecopilot.answering.Chunks.evidence;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.answering.CitationValidator.Check;
import com.codebasecopilot.answering.CitationValidator.Invalid;
import com.codebasecopilot.answering.CitationValidator.Outcome;
import com.codebasecopilot.answering.CitationValidator.Valid;
import com.codebasecopilot.history.AnswerStatus;
import com.codebasecopilot.history.EvidenceValidation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CitationValidatorTest {

    // E1 covers lines 10-20 and E2 lines 1-5.
    private static final List<ContextEvidence> EVIDENCE =
            List.of(evidence("E1", "a.java", 10, 20), evidence("E2", "b.java", 1, 5));

    @Test
    void acceptsAnAnsweredOutputWithValidCitations() {
        Valid valid =
                valid(
                        output(
                                "ANSWERED",
                                "Calcula em A [E1] e usa B [E2].",
                                cite("E1", 12, 15),
                                cite("E2", null, null)));

        assertThat(valid.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(valid.text()).isEqualTo("Calcula em A [E1] e usa B [E2].");
        assertThat(valid.warnings()).isEmpty();
        assertThat(valid.invalidCitationCount()).isZero();
        assertThat(valid.checks())
                .containsEntry("E1", new Check(EvidenceValidation.CITED, 12, 15))
                .containsEntry("E2", new Check(EvidenceValidation.CITED, null, null));
    }

    @Test
    void marksEvidenceThatWasNotCited() {
        Valid valid = valid(output("ANSWERED", "Em A [E1].", cite("E1", null, null)));

        assertThat(valid.checks()).containsEntry("E2", Check.NOT_CITED);
    }

    @Test
    void discardsAnInventedLabelAndItsMarker() {
        Valid valid =
                valid(
                        output(
                                "ANSWERED",
                                "Em A [E1] e em C [E7].",
                                cite("E1", null, null),
                                cite("E7", null, null)));

        assertThat(valid.text()).isEqualTo("Em A [E1] e em C.");
        assertThat(valid.invalidCitationCount()).isEqualTo(1);
        assertThat(valid.warnings()).containsExactly(AnswerWarning.INVALID_CITATIONS_REMOVED);
        assertThat(valid.checks()).doesNotContainKey("E7");
        assertThat(valid.status()).isEqualTo(AnswerStatus.ANSWERED);
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                // start, end
                "9,    15",
                "12,   21",
                "15,   12",
                "21,   null",
                "null, 9"
            })
    void discardsLinesOutsideTheEvidenceOrInverted(Integer start, Integer end) {
        Valid valid =
                valid(
                        output(
                                "ANSWERED",
                                "Em A [E1] e B [E2].",
                                cite("E1", start, end),
                                cite("E2", null, null)));

        assertThat(valid.checks()).containsEntry("E1", Check.INVALID_RANGE);
        assertThat(valid.text()).isEqualTo("Em A e B [E2].");
        assertThat(valid.invalidCitationCount()).isEqualTo(1);
        assertThat(valid.warnings()).containsExactly(AnswerWarning.INVALID_CITATIONS_REMOVED);
    }

    @Test
    void oneMissingLineMeansTheEvidenceBoundary() {
        Valid valid =
                valid(
                        output(
                                "ANSWERED",
                                "A [E1] B [E2].",
                                cite("E1", 15, null),
                                cite("E2", null, 3)));

        assertThat(valid.checks())
                .containsEntry("E1", new Check(EvidenceValidation.CITED, 15, 20))
                .containsEntry("E2", new Check(EvidenceValidation.CITED, 1, 3));
    }

    @Test
    void severalCitationsOfOneEvidenceCoverAllTheirLines() {
        Valid valid =
                valid(
                        output(
                                "ANSWERED",
                                "A [E1].",
                                cite("E1", 16, 18),
                                cite("E1", 11, 12),
                                cite("E1", 30, 40)));

        assertThat(valid.checks()).containsEntry("E1", new Check(EvidenceValidation.CITED, 11, 18));
        assertThat(valid.invalidCitationCount()).isEqualTo(1);
    }

    @Test
    void aWholeCitationWinsOverNarrowedOnes() {
        Valid valid =
                valid(output("ANSWERED", "A [E1].", cite("E1", 11, 12), cite("E1", null, null)));

        assertThat(valid.checks())
                .containsEntry("E1", new Check(EvidenceValidation.CITED, null, null));
    }

    @Test
    void removesMarkersWithoutAValidCitation() {
        Valid valid = valid(output("ANSWERED", "A [E1]. B [E2]. C [E9].", cite("E1", null, null)));

        assertThat(valid.text()).isEqualTo("A [E1]. B. C.");
        assertThat(valid.invalidCitationCount()).isEqualTo(2);
        assertThat(valid.checks()).containsEntry("E2", Check.NOT_CITED);
    }

    @Test
    void keepsACitationWithoutAMarker() {
        Valid valid = valid(output("ANSWERED", "A resposta.", cite("E2", 2, 4)));

        assertThat(valid.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(valid.checks()).containsEntry("E2", new Check(EvidenceValidation.CITED, 2, 4));
        assertThat(valid.warnings()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"ANSWERED", "PARTIAL"})
    void anAnswerWithoutValidCitationsBecomesPartial(String declared) {
        Valid valid = valid(output(declared, "A [E7].", cite("E7", null, null)));

        assertThat(valid.status()).isEqualTo(AnswerStatus.PARTIAL);
        assertThat(valid.text()).isEqualTo("A.");
        assertThat(valid.warnings())
                .containsExactly(
                        AnswerWarning.INVALID_CITATIONS_REMOVED, AnswerWarning.NO_VALID_CITATIONS);
    }

    @Test
    void anAnswerWithNoCitationsAtAllBecomesPartial() {
        Valid valid = valid(output("ANSWERED", "Sem fonte."));

        assertThat(valid.status()).isEqualTo(AnswerStatus.PARTIAL);
        assertThat(valid.warnings()).containsExactly(AnswerWarning.NO_VALID_CITATIONS);
        assertThat(valid.invalidCitationCount()).isZero();
    }

    @Test
    void keepsADeclaredPartialWithValidCitations() {
        Valid valid = valid(output("PARTIAL", "Só parte [E1].", cite("E1", null, null)));

        assertThat(valid.status()).isEqualTo(AnswerStatus.PARTIAL);
        assertThat(valid.warnings()).isEmpty();
    }

    @Test
    void anAbstentionNeedsNoCitations() {
        Valid valid = valid(output("ABSTAINED", "Não há evidência suficiente."));

        assertThat(valid.status()).isEqualTo(AnswerStatus.ABSTAINED);
        assertThat(valid.warnings()).isEmpty();
        assertThat(valid.checks().values()).containsOnly(Check.NOT_CITED);
    }

    @Test
    void anAbstentionMayHaveNoText() {
        assertThat(valid(output("ABSTAINED", "")).status()).isEqualTo(AnswerStatus.ABSTAINED);
    }

    @Test
    void anAbstentionStillReportsInvalidCitations() {
        Valid valid = valid(output("ABSTAINED", "Faltam dados [E5].", cite("E5", null, null)));

        assertThat(valid.status()).isEqualTo(AnswerStatus.ABSTAINED);
        assertThat(valid.text()).isEqualTo("Faltam dados.");
        assertThat(valid.warnings()).containsExactly(AnswerWarning.INVALID_CITATIONS_REMOVED);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "not json",
                "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"citations\":[",
                "[]",
                "null",
                "{\"status\":\"ANSWERED\",\"answer\":\"A\"}",
                "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"citations\":[],\"extra\":1}",
                "{\"status\":\"DONE\",\"answer\":\"A\",\"citations\":[]}",
                "{\"status\":1,\"answer\":\"A\",\"citations\":[]}",
                "{\"status\":\"ANSWERED\",\"answer\":null,\"citations\":[]}",
                "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"citations\":{}}",
                "{\"status\":\"ANSWERED\",\"answer\":\"  \",\"citations\":[]}",
                "{\"status\":\"PARTIAL\",\"answer\":\"\",\"citations\":[]}",
                "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"citations\":[\"E1\"]}",
                "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"citations\":[{\"evidence\":\"E1\"}]}",
                "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"citations\":"
                        + "[{\"evidence\":1,\"startLine\":null,\"endLine\":null}]}",
                "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"citations\":"
                        + "[{\"evidence\":\"E1\",\"startLine\":\"12\",\"endLine\":null}]}",
                "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"citations\":"
                        + "[{\"evidence\":\"E1\",\"startLine\":12.5,\"endLine\":null}]}",
                "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"citations\":"
                        + "[{\"evidence\":\"E1\",\"startLine\":99999999999,\"endLine\":null}]}",
                "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"citations\":"
                        + "[{\"evidence\":\"E1\",\"startLine\":null,\"endLine\":null,\"x\":1}]}"
            })
    void rejectsOutputThatIsNotJsonOrDoesNotMatchTheSchema(String output) {
        assertThat(CitationValidator.validate(output, EVIDENCE)).isInstanceOf(Invalid.class);
    }

    private static Valid valid(String output) {
        Outcome outcome = CitationValidator.validate(output, EVIDENCE);
        assertThat(outcome).isInstanceOf(Valid.class);
        return (Valid) outcome;
    }

    private static String output(String status, String answer, String... citations) {
        return "{\"status\":\"%s\",\"answer\":\"%s\",\"citations\":[%s]}"
                .formatted(status, answer, String.join(",", citations));
    }

    private static String cite(String label, Integer start, Integer end) {
        return "{\"evidence\":\"%s\",\"startLine\":%s,\"endLine\":%s}".formatted(label, start, end);
    }
}
