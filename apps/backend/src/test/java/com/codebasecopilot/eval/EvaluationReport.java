package com.codebasecopilot.eval;

import com.codebasecopilot.eval.QuestionSet.Question;
import com.codebasecopilot.eval.RetrievalMetrics.QuestionMetrics;
import com.codebasecopilot.eval.RetrievalMetrics.RetrievedChunk;
import com.codebasecopilot.eval.RetrievalMetrics.Summary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

// Results of one evaluation run as JSON (everything but the latencies is the same on every run
// with the same corpus, versions and cached vectors) and a Markdown summary in pt-BR.
public final class EvaluationReport {

    // config: what the run measured, for the reader; it is not interpreted.
    public record Run(
            String date, String name, Map<String, Object> config, List<CorpusRun> corpora) {}

    public record CorpusRun(
            QuestionSet.Corpus corpus,
            String manifestSha256,
            String chunkingVersion,
            int files,
            int chunks,
            List<QuestionRun> questions,
            Map<String, List<Long>> latencyMillis) {}

    // One ranking per variant (LEXICAL, HYBRID…), in the order the variants were measured.
    public record QuestionRun(Question question, Map<String, List<RetrievedChunk>> rankings) {}

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private EvaluationReport() {}

    // Writes <date>-<name>.json and .md and returns the JSON file.
    public static Path write(Path directory, Run run) throws IOException {
        Files.createDirectories(directory);
        Path json = directory.resolve(run.date() + "-" + run.name() + ".json");
        Files.writeString(json, JSON.writeValueAsString(json(run)) + "\n", StandardCharsets.UTF_8);
        Files.writeString(
                directory.resolve(run.date() + "-" + run.name() + ".md"),
                markdown(run),
                StandardCharsets.UTF_8);
        return json;
    }

    public static Map<String, Object> json(Run run) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("date", run.date());
        root.put("name", run.name());
        root.put("config", run.config());
        root.put("metrics", metrics(run));
        List<Object> corpora = new ArrayList<>();
        for (CorpusRun corpus : run.corpora()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", corpus.corpus().name());
            c.put("repository", corpus.corpus().repository());
            c.put("commit", corpus.corpus().commit());
            c.put("manifestSha256", corpus.manifestSha256());
            c.put("chunkingVersion", corpus.chunkingVersion());
            c.put("files", corpus.files());
            c.put("chunks", corpus.chunks());
            c.put("latencyMillis", latencies(corpus.latencyMillis()));
            List<Object> questions = new ArrayList<>();
            for (QuestionRun q : corpus.questions()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", q.question().id());
                entry.put("category", q.question().category().name());
                entry.put("language", q.question().language());
                Map<String, Object> variants = new LinkedHashMap<>();
                q.rankings()
                        .forEach(
                                (variant, ranking) -> {
                                    Map<String, Object> v = new LinkedHashMap<>();
                                    v.putAll(metrics(RetrievalMetrics.of(q.question(), ranking)));
                                    v.put(
                                            "top",
                                            top(ranking, RetrievalMetrics.MRR_K).stream()
                                                    .map(EvaluationReport::reference)
                                                    .toList());
                                    variants.put(variant, v);
                                });
                entry.put("variants", variants);
                questions.add(entry);
            }
            c.put("questions", questions);
            corpora.add(c);
        }
        root.put("corpora", corpora);
        return root;
    }

    // Per variant: all questions, then per corpus, category and language.
    private static Map<String, Object> metrics(Run run) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (String variant : variants(run)) {
            Map<String, Object> byVariant = new LinkedHashMap<>();
            byVariant.put("all", summary(summarize(run, variant, q -> true)));
            byVariant.put("corpus", grouped(run, variant, q -> corpusOf(run, q).corpus().name()));
            byVariant.put("category", grouped(run, variant, q -> q.question().category().name()));
            byVariant.put("language", grouped(run, variant, q -> q.question().language()));
            metrics.put(variant, byVariant);
        }
        return metrics;
    }

    private static Map<String, Object> grouped(
            Run run, String variant, Function<QuestionRun, String> key) {
        Map<String, Object> groups = new TreeMap<>();
        for (String group : groups(run, key)) {
            groups.put(group, summary(summarize(run, variant, q -> key.apply(q).equals(group))));
        }
        return groups;
    }

    private static List<String> groups(Run run, Function<QuestionRun, String> key) {
        return run.corpora().stream()
                .flatMap(c -> c.questions().stream())
                .map(key)
                .distinct()
                .sorted()
                .toList();
    }

    private static Summary summarize(Run run, String variant, Predicate<QuestionRun> filter) {
        return RetrievalMetrics.summarize(
                run.corpora().stream()
                        .flatMap(c -> c.questions().stream())
                        .filter(filter)
                        .filter(q -> q.rankings().containsKey(variant))
                        .map(q -> RetrievalMetrics.of(q.question(), q.rankings().get(variant)))
                        .toList());
    }

    private static List<String> variants(Run run) {
        return run.corpora().stream()
                .flatMap(c -> c.questions().stream())
                .flatMap(q -> q.rankings().keySet().stream())
                .distinct()
                .toList();
    }

    private static CorpusRun corpusOf(Run run, QuestionRun question) {
        return run.corpora().stream()
                .filter(c -> c.questions().contains(question))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> summary(Summary s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questions", s.questions());
        s.recall().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.put("recall@" + e.getKey(), round(e.getValue())));
        out.put("mrr@" + RetrievalMetrics.MRR_K, round(s.mrr()));
        out.put("precision@" + RetrievalMetrics.PRECISION_K, round(s.precision()));
        out.put(
                "fileCoverage@" + RetrievalMetrics.COVERAGE_K,
                s.fileCoverage() == null ? null : round(s.fileCoverage()));
        out.put("coverageQuestions", s.coverageQuestions());
        return out;
    }

    private static Map<String, Object> metrics(QuestionMetrics m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.recall().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.put("recall@" + e.getKey(), round(e.getValue())));
        out.put("mrr@" + RetrievalMetrics.MRR_K, round(m.mrr()));
        out.put("precision@" + RetrievalMetrics.PRECISION_K, round(m.precision()));
        if (m.fileCoverage() != null) {
            out.put("fileCoverage@" + RetrievalMetrics.COVERAGE_K, round(m.fileCoverage()));
        }
        out.put("firstRelevant", m.firstRelevant());
        return out;
    }

    private static Map<String, Object> latencies(Map<String, List<Long>> millis) {
        Map<String, Object> out = new LinkedHashMap<>();
        millis.forEach(
                (channel, values) -> {
                    Map<String, Object> l = new LinkedHashMap<>();
                    l.put("mean", round(mean(values)));
                    l.put("p95", p95(values));
                    out.put(channel, l);
                });
        return out;
    }

    static String markdown(Run run) {
        StringBuilder md = new StringBuilder();
        md.append("# Avaliação de recuperação — ")
                .append(run.date())
                .append(" (")
                .append(run.name());
        md.append(
                ")\n\nGerado pelo `RetrievalEvaluationIT`; os detalhes por pergunta estão no JSON");
        md.append(" de mesmo nome.\n\n## Configuração\n\n");
        run.config()
                .forEach(
                        (key, value) ->
                                md.append("- `")
                                        .append(key)
                                        .append("`: ")
                                        .append(value == null ? "—" : value)
                                        .append('\n'));
        md.append(
                "\n## Corpora\n\n| Corpus | Commit | Arquivos | Chunks | Perguntas | Chunking |\n");
        md.append("|---|---|---|---|---|---|\n");
        for (CorpusRun c : run.corpora()) {
            md.append("| ")
                    .append(c.corpus().name())
                    .append(" | ")
                    .append(
                            c.corpus().commit() == null
                                    ? "versionado"
                                    : "`" + c.corpus().commit().substring(0, 7) + "`")
                    .append(" | ")
                    .append(c.files())
                    .append(" | ")
                    .append(c.chunks())
                    .append(" | ")
                    .append(c.questions().size())
                    .append(" | `")
                    .append(c.chunkingVersion())
                    .append("` |\n");
        }
        List<String> variants = variants(run);
        md.append("\n## Todas as perguntas\n\n");
        table(md, "Variante", variants, variant -> summarize(run, variant, q -> true));
        for (CorpusRun c : run.corpora()) {
            md.append("\n## Corpus ").append(c.corpus().name()).append("\n\n");
            table(
                    md,
                    "Variante",
                    variants,
                    variant -> summarize(run, variant, q -> c.questions().contains(q)));
        }
        for (var dimension :
                List.<Map.Entry<String, Function<QuestionRun, String>>>of(
                        Map.entry("categoria", q -> q.question().category().name()),
                        Map.entry("idioma", q -> q.question().language()))) {
            md.append("\n## Por ").append(dimension.getKey()).append(" (Recall@10 / MRR@10)\n\n");
            md.append("| ").append(capitalize(dimension.getKey())).append(" | Perguntas");
            variants.forEach(v -> md.append(" | ").append(v));
            md.append(" |\n|---|---");
            variants.forEach(v -> md.append("|---"));
            md.append("|\n");
            for (String group : groups(run, dimension.getValue())) {
                Predicate<QuestionRun> inGroup = q -> dimension.getValue().apply(q).equals(group);
                md.append("| ")
                        .append(group)
                        .append(" | ")
                        .append(summarize(run, variants.getFirst(), inGroup).questions());
                for (String variant : variants) {
                    Summary s = summarize(run, variant, inGroup);
                    md.append(" | ")
                            .append(format(s.recall().get(10)))
                            .append(" / ")
                            .append(format(s.mrr()));
                }
                md.append(" |\n");
            }
        }
        md.append(
                "\n## Latência por canal (ms, média / p95)\n\n| Corpus | Canal | Média | p95 |\n");
        md.append("|---|---|---|---|\n");
        for (CorpusRun c : run.corpora()) {
            c.latencyMillis()
                    .forEach(
                            (channel, values) ->
                                    md.append("| ")
                                            .append(c.corpus().name())
                                            .append(" | ")
                                            .append(channel)
                                            .append(" | ")
                                            .append(
                                                    String.format(
                                                            Locale.ROOT, "%.1f", mean(values)))
                                            .append(" | ")
                                            .append(p95(values))
                                            .append(" |\n"));
        }
        md.append("\nA latência varia entre execuções; as demais métricas não (mesmo corpus,");
        md.append(" versões e vetores em cache).\n");
        return md.toString();
    }

    private static void table(
            StringBuilder md, String header, List<String> rows, Function<String, Summary> summary) {
        md.append("| ").append(header).append(" | Perguntas");
        RetrievalMetrics.RECALL_KS.forEach(k -> md.append(" | Recall@").append(k));
        md.append(" | MRR@")
                .append(RetrievalMetrics.MRR_K)
                .append(" | Precision@")
                .append(RetrievalMetrics.PRECISION_K)
                .append(" | Cobertura de arquivos@")
                .append(RetrievalMetrics.COVERAGE_K)
                .append(" |\n|---|---");
        RetrievalMetrics.RECALL_KS.forEach(k -> md.append("|---"));
        md.append("|---|---|---|\n");
        for (String row : rows) {
            Summary s = summary.apply(row);
            md.append("| ").append(row).append(" | ").append(s.questions());
            RetrievalMetrics.RECALL_KS.forEach(
                    k -> md.append(" | ").append(format(s.recall().get(k))));
            md.append(" | ")
                    .append(format(s.mrr()))
                    .append(" | ")
                    .append(format(s.precision()))
                    .append(" | ")
                    .append(
                            s.fileCoverage() == null
                                    ? "—"
                                    : format(s.fileCoverage()) + " (" + s.coverageQuestions() + ")")
                    .append(" |\n");
        }
    }

    private static String reference(RetrievedChunk chunk) {
        return String.join(",", chunk.paths()) + ":" + chunk.startLine() + "-" + chunk.endLine();
    }

    private static List<RetrievedChunk> top(List<RetrievedChunk> ranking, int k) {
        return ranking.subList(0, Math.min(k, ranking.size()));
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }

    private static double mean(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    static long p95(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        long[] sorted = values.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(sorted);
        return sorted[(int) Math.ceil(sorted.length * 0.95) - 1];
    }
}
