package com.codebasecopilot.eval;

import com.codebasecopilot.eval.QuestionSet.Question;
import com.codebasecopilot.eval.RetrievalMetrics.RetrievedChunk;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

// Automatic answer metrics of docs/08-EVALUATION.md §4, over the answers as the API returns them.
// Citations are counted per evidence: the validator merges repeated citations of one evidence.
// Correctness and factual support are left to the human audit.
public final class AnswerQuality {

    public static final List<String> ANSWERED = List.of("ANSWERED", "PARTIAL");
    public static final String ABSTAINED = "ABSTAINED";
    public static final String FAILED = "FAILED";
    public static final String CITED = "CITED";

    // paths: every path of the snapshot with this content; the cited lines are null when the
    // whole evidence is cited or when it was not cited.
    public record Evidence(
            String label,
            List<String> paths,
            int startLine,
            int endLine,
            @Nullable Integer citedStartLine,
            @Nullable Integer citedEndLine,
            String validation,
            String snippet) {

        public boolean cited() {
            return validation.equals(CITED);
        }

        public int citedStart() {
            return citedStartLine == null ? startLine : citedStartLine;
        }

        public int citedEnd() {
            return citedEndLine == null ? endLine : citedEndLine;
        }
    }

    public record Answer(
            String status,
            @Nullable String text,
            int invalidCitations,
            List<Evidence> evidence,
            @Nullable String errorCode) {}

    // relevantCitation is null when not measured: no answer, or a question that must abstain.
    public record QuestionMetrics(
            String status,
            boolean mustAbstain,
            int citations,
            int validCitations,
            boolean supported,
            @Nullable Boolean relevantCitation,
            List<String> leaks) {

        public boolean answered() {
            return ANSWERED.contains(status);
        }

        public boolean abstained() {
            return status.equals(ABSTAINED);
        }
    }

    // Each rate is null when its denominator is zero.
    public record Summary(
            int questions,
            int answered,
            int failed,
            int citations,
            int validCitations,
            int supported,
            int relevant,
            int relevanceMeasured,
            int mustAbstain,
            int correctAbstentions,
            int shouldAnswer,
            int undueAbstentions,
            int leaks) {

        public @Nullable Double citationValidity() {
            return rate(validCitations, citations);
        }

        public @Nullable Double supportedRate() {
            return rate(supported, answered);
        }

        public @Nullable Double relevantRate() {
            return rate(relevant, relevanceMeasured);
        }

        public @Nullable Double abstentionRate() {
            return rate(correctAbstentions, mustAbstain);
        }

        public @Nullable Double undueAbstentionRate() {
            return rate(undueAbstentions, shouldAnswer);
        }
    }

    private AnswerQuality() {}

    public static QuestionMetrics of(Question question, Answer answer) {
        List<Evidence> cited = answer.evidence().stream().filter(Evidence::cited).toList();
        boolean answered = ANSWERED.contains(answer.status());
        Boolean relevant = null;
        if (answered && !question.mustAbstain()) {
            relevant =
                    cited.stream()
                            .anyMatch(
                                    e ->
                                            question.expected().stream()
                                                    .anyMatch(
                                                            range ->
                                                                    RetrievalMetrics.relevant(
                                                                            new RetrievedChunk(
                                                                                    e.paths(),
                                                                                    e.citedStart(),
                                                                                    e.citedEnd()),
                                                                            range)));
        }
        return new QuestionMetrics(
                answer.status(),
                question.mustAbstain(),
                cited.size() + answer.invalidCitations(),
                cited.size(),
                answered && !cited.isEmpty(),
                relevant,
                leaks(question, answer.text()));
    }

    public static Summary summarize(List<QuestionMetrics> metrics) {
        int answered = 0;
        int failed = 0;
        int citations = 0;
        int valid = 0;
        int supported = 0;
        int relevant = 0;
        int measured = 0;
        int mustAbstain = 0;
        int correct = 0;
        int shouldAnswer = 0;
        int undue = 0;
        int leaks = 0;
        for (QuestionMetrics m : metrics) {
            answered += m.answered() ? 1 : 0;
            failed += m.status().equals(FAILED) ? 1 : 0;
            citations += m.citations();
            valid += m.validCitations();
            supported += m.supported() ? 1 : 0;
            if (m.relevantCitation() != null) {
                measured++;
                relevant += m.relevantCitation() ? 1 : 0;
            }
            if (m.mustAbstain()) {
                mustAbstain++;
                correct += m.abstained() ? 1 : 0;
            } else {
                shouldAnswer++;
                undue += m.abstained() ? 1 : 0;
            }
            leaks += m.leaks().isEmpty() ? 0 : 1;
        }
        return new Summary(
                metrics.size(),
                answered,
                failed,
                citations,
                valid,
                supported,
                relevant,
                measured,
                mustAbstain,
                correct,
                shouldAnswer,
                undue,
                leaks);
    }

    private static List<String> leaks(Question question, @Nullable String text) {
        if (text == null) {
            return List.of();
        }
        String answer = text.toLowerCase(Locale.ROOT);
        return question.mustNotContain().stream()
                .filter(forbidden -> answer.contains(forbidden.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static @Nullable Double rate(int count, int total) {
        return total == 0 ? null : (double) count / total;
    }
}
