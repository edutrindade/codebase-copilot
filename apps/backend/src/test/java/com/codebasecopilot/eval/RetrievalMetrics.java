package com.codebasecopilot.eval;

import com.codebasecopilot.eval.QuestionSet.Category;
import com.codebasecopilot.eval.QuestionSet.ExpectedRange;
import com.codebasecopilot.eval.QuestionSet.Question;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

// Retrieval metrics of docs/08-EVALUATION.md §3, by line range: a chunk is relevant when one of
// its paths is the range's path (or one of its otherPaths) and its lines overlap the range.
public final class RetrievalMetrics {

    public static final List<Integer> RECALL_KS = List.of(5, 10, 20);
    public static final int MRR_K = 10;
    public static final int PRECISION_K = 10;
    public static final int COVERAGE_K = 10;
    public static final int DEPTH = 20;

    // paths lists every file of the snapshot with the chunk's content.
    public record RetrievedChunk(List<String> paths, int startLine, int endLine) {}

    // fileCoverage is only measured on CROSS_FILE_FLOW questions; firstRelevant is 1-based.
    public record QuestionMetrics(
            Map<Integer, Double> recall,
            double mrr,
            double precision,
            @Nullable Double fileCoverage,
            @Nullable Integer firstRelevant) {}

    public record Summary(
            int questions,
            Map<Integer, Double> recall,
            double mrr,
            double precision,
            @Nullable Double fileCoverage,
            int coverageQuestions) {}

    private RetrievalMetrics() {}

    public static boolean relevant(RetrievedChunk chunk, ExpectedRange range) {
        return chunk.startLine() <= range.endLine()
                && chunk.endLine() >= range.startLine()
                && chunk.paths().stream().anyMatch(path -> file(range).contains(path));
    }

    public static QuestionMetrics of(Question question, List<RetrievedChunk> ranking) {
        if (question.mustAbstain()) {
            throw new IllegalArgumentException(
                    question.id() + ": questions that must abstain have no retrieval metrics");
        }
        List<ExpectedRange> expected = question.expected();
        Map<Integer, Double> recall = new LinkedHashMap<>();
        for (int k : RECALL_KS) {
            List<RetrievedChunk> top = top(ranking, k);
            long found =
                    expected.stream()
                            .filter(range -> top.stream().anyMatch(c -> relevant(c, range)))
                            .count();
            recall.put(k, (double) found / expected.size());
        }
        Integer firstRelevant = null;
        for (int i = 0; i < ranking.size() && firstRelevant == null; i++) {
            if (isRelevant(ranking.get(i), expected)) {
                firstRelevant = i + 1;
            }
        }
        double mrr = firstRelevant != null && firstRelevant <= MRR_K ? 1.0 / firstRelevant : 0;
        long relevantInTop =
                top(ranking, PRECISION_K).stream().filter(c -> isRelevant(c, expected)).count();
        return new QuestionMetrics(
                Map.copyOf(recall),
                mrr,
                (double) relevantInTop / PRECISION_K,
                question.category() == Category.CROSS_FILE_FLOW
                        ? fileCoverage(expected, top(ranking, COVERAGE_K))
                        : null,
                firstRelevant);
    }

    public static Summary summarize(List<QuestionMetrics> metrics) {
        Map<Integer, Double> recall = new LinkedHashMap<>();
        for (int k : RECALL_KS) {
            recall.put(k, mean(metrics.stream().map(m -> m.recall().get(k)).toList()));
        }
        List<Double> coverage =
                metrics.stream()
                        .map(QuestionMetrics::fileCoverage)
                        .filter(Objects::nonNull)
                        .toList();
        return new Summary(
                metrics.size(),
                Map.copyOf(recall),
                mean(metrics.stream().map(QuestionMetrics::mrr).toList()),
                mean(metrics.stream().map(QuestionMetrics::precision).toList()),
                coverage.isEmpty() ? null : mean(coverage),
                coverage.size());
    }

    // A file is the range's path together with its otherPaths, since they hold the same content.
    private static double fileCoverage(List<ExpectedRange> expected, List<RetrievedChunk> top) {
        Set<Set<String>> files = new LinkedHashSet<>();
        expected.forEach(range -> files.add(file(range)));
        long found =
                files.stream()
                        .filter(
                                file ->
                                        top.stream()
                                                .anyMatch(
                                                        c ->
                                                                c.paths().stream()
                                                                        .anyMatch(file::contains)))
                        .count();
        return (double) found / files.size();
    }

    private static Set<String> file(ExpectedRange range) {
        Set<String> paths = new LinkedHashSet<>(List.of(range.path()));
        paths.addAll(range.otherPaths());
        return paths;
    }

    private static boolean isRelevant(RetrievedChunk chunk, List<ExpectedRange> expected) {
        return expected.stream().anyMatch(range -> relevant(chunk, range));
    }

    private static List<RetrievedChunk> top(List<RetrievedChunk> ranking, int k) {
        return ranking.subList(0, Math.min(k, ranking.size()));
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
