package com.codebasecopilot.eval;

import com.codebasecopilot.eval.AnswerQuality.Answer;
import com.codebasecopilot.eval.AnswerQuality.Evidence;
import com.codebasecopilot.eval.AnswerQuality.QuestionMetrics;
import com.codebasecopilot.eval.AnswerQuality.Summary;
import com.codebasecopilot.eval.QuestionSet.ExpectedRange;
import com.codebasecopilot.eval.QuestionSet.Question;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

// Results of one answer evaluation run as JSON and a Markdown file in pt-BR with the summary and,
// per question, the answer next to the cited snippets for the human audit (docs/08 §4).
public final class AnswerEvaluationReport {

    // config: what the run measured, for the reader; it is not interpreted.
    public record Run(
            String date, String name, Map<String, Object> config, List<CorpusRun> corpora) {}

    public record CorpusRun(
            QuestionSet.Corpus corpus,
            String manifestSha256,
            String chunkingVersion,
            int files,
            int chunks,
            List<QuestionRun> questions) {}

    public record QuestionRun(Question question, Answer answer, Usage usage) {}

    // As the answer API reports them. A cached answer keeps the tokens and cost of the original
    // call, but its latency does not measure the provider.
    public record Usage(
            @Nullable Integer inputTokens,
            @Nullable Integer outputTokens,
            @Nullable BigDecimal estimatedCost,
            @Nullable Long latencyMs,
            @Nullable Long generationMs,
            boolean cached) {}

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final Pattern BACKTICKS = Pattern.compile("`+");

    private AnswerEvaluationReport() {}

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
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("all", block(questions(run, q -> true)));
        metrics.put("corpus", grouped(run, corpusName(run)));
        metrics.put("category", grouped(run, q -> q.question().category().name()));
        metrics.put("language", grouped(run, q -> q.question().language()));
        root.put("metrics", metrics);
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
            c.put(
                    "questions",
                    corpus.questions().stream().map(AnswerEvaluationReport::question).toList());
            corpora.add(c);
        }
        root.put("corpora", corpora);
        return root;
    }

    private static Map<String, Object> question(QuestionRun run) {
        Question q = run.question();
        Answer a = run.answer();
        QuestionMetrics m = AnswerQuality.of(q, a);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", q.id());
        entry.put("category", q.category().name());
        entry.put("language", q.language());
        entry.put("mustAbstain", q.mustAbstain());
        entry.put("status", a.status());
        entry.put("errorCode", a.errorCode());
        entry.put("answer", a.text());
        entry.put("invalidCitations", a.invalidCitations());
        List<Object> evidence = new ArrayList<>();
        for (Evidence e : a.evidence()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", e.label());
            item.put("paths", e.paths());
            item.put("lines", e.startLine() + "-" + e.endLine());
            item.put("validation", e.validation());
            item.put("citedLines", e.cited() ? e.citedStart() + "-" + e.citedEnd() : null);
            evidence.add(item);
        }
        entry.put("evidence", evidence);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("citations", m.citations());
        metrics.put("validCitations", m.validCitations());
        metrics.put("supported", m.supported());
        metrics.put("relevantCitation", m.relevantCitation());
        metrics.put("leaks", m.leaks());
        entry.put("metrics", metrics);
        Usage u = run.usage();
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("inputTokens", u.inputTokens());
        usage.put("outputTokens", u.outputTokens());
        usage.put("estimatedCost", u.estimatedCost());
        usage.put("latencyMs", u.latencyMs());
        usage.put("generationMs", u.generationMs());
        usage.put("cached", u.cached());
        entry.put("usage", usage);
        return entry;
    }

    private static Map<String, Object> grouped(Run run, Function<QuestionRun, String> key) {
        Map<String, Object> groups = new TreeMap<>();
        for (String group : groups(run, key)) {
            groups.put(group, block(questions(run, q -> key.apply(q).equals(group))));
        }
        return groups;
    }

    private static Map<String, Object> block(List<QuestionRun> questions) {
        Summary s = summarize(questions);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questions", s.questions());
        out.put("answered", s.answered());
        out.put("failed", s.failed());
        out.put("citations", s.citations());
        out.put("validCitations", s.validCitations());
        out.put("citationValidity", round(s.citationValidity()));
        out.put("supported", s.supported());
        out.put("supportedRate", round(s.supportedRate()));
        out.put("relevant", s.relevant());
        out.put("relevanceMeasured", s.relevanceMeasured());
        out.put("relevantRate", round(s.relevantRate()));
        out.put("mustAbstain", s.mustAbstain());
        out.put("correctAbstentions", s.correctAbstentions());
        out.put("abstentionRate", round(s.abstentionRate()));
        out.put("shouldAnswer", s.shouldAnswer());
        out.put("undueAbstentions", s.undueAbstentions());
        out.put("undueAbstentionRate", round(s.undueAbstentionRate()));
        out.put("leaks", s.leaks());
        out.put("usage", usage(questions));
        return out;
    }

    private static Map<String, Object> usage(List<QuestionRun> questions) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inputTokens", stats(values(questions, u -> toLong(u.inputTokens()))));
        out.put("outputTokens", stats(values(questions, u -> toLong(u.outputTokens()))));
        List<BigDecimal> costs =
                questions.stream()
                        .map(q -> q.usage().estimatedCost())
                        .filter(Objects::nonNull)
                        .toList();
        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("answers", costs.size());
        cost.put("total", costs.isEmpty() ? null : total(costs));
        cost.put("mean", costs.isEmpty() ? null : mean(costs));
        out.put("estimatedCostUsd", cost);
        List<QuestionRun> uncached = questions.stream().filter(q -> !q.usage().cached()).toList();
        out.put("latencyMs", stats(values(uncached, Usage::latencyMs)));
        out.put("generationMs", stats(values(uncached, Usage::generationMs)));
        out.put("cachedAnswers", questions.size() - uncached.size());
        return out;
    }

    private static Map<String, Object> stats(List<Long> values) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answers", values.size());
        out.put("mean", values.isEmpty() ? null : round(meanOf(values)));
        out.put("p95", values.isEmpty() ? null : EvaluationReport.p95(values));
        return out;
    }

    static String markdown(Run run) {
        StringBuilder md = new StringBuilder();
        md.append("# Avaliação de respostas — ")
                .append(run.date())
                .append(" (")
                .append(run.name())
                .append(")\n\nGerado pelo `AnswerEvaluationIT`; os detalhes por pergunta estão")
                .append(" no JSON de mesmo nome.\n\n## Configuração\n\n");
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

        md.append("\n## Métricas automáticas\n\n");
        md.append("Validade: citações válidas / emitidas, por evidência. Sustentadas e citação");
        md.append(" relevante: entre as respostas `ANSWERED` e `PARTIAL` (a relevância só nas");
        md.append(" perguntas com intervalo esperado). Abstenção correta: nas perguntas com");
        md.append(" `mustAbstain`; indevida: nas demais. Vazamento: resposta com algum texto de");
        md.append(" `mustNotContain`.\n\n");
        Function<QuestionRun, String> corpus = corpusName(run);
        List<Map.Entry<String, Predicate<QuestionRun>>> rows = new ArrayList<>();
        rows.add(Map.entry("Todas", q -> true));
        for (String name : groups(run, corpus)) {
            rows.add(Map.entry("Corpus " + name, q -> corpus.apply(q).equals(name)));
        }
        qualityTable(md, "Grupo", run, rows);
        for (var dimension :
                List.<Map.Entry<String, Function<QuestionRun, String>>>of(
                        Map.entry("Categoria", q -> q.question().category().name()),
                        Map.entry("Idioma", q -> q.question().language()))) {
            md.append("\n### Por ").append(dimension.getKey().toLowerCase(Locale.ROOT));
            md.append("\n\n");
            List<Map.Entry<String, Predicate<QuestionRun>>> groupRows = new ArrayList<>();
            for (String group : groups(run, dimension.getValue())) {
                groupRows.add(Map.entry(group, q -> dimension.getValue().apply(q).equals(group)));
            }
            qualityTable(md, dimension.getKey(), run, groupRows);
        }

        md.append("\n## Tokens, custo e latência\n\n");
        md.append("| Grupo | Entrada (média / p95) | Saída (média / p95) | Custo total (USD) |");
        md.append(" Custo médio (USD) | Latência ms (média / p95) | Em cache |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (var row : rows) {
            List<QuestionRun> questions = questions(run, row.getValue());
            List<BigDecimal> costs =
                    questions.stream()
                            .map(q -> q.usage().estimatedCost())
                            .filter(Objects::nonNull)
                            .toList();
            List<QuestionRun> uncached =
                    questions.stream().filter(q -> !q.usage().cached()).toList();
            md.append("| ")
                    .append(row.getKey())
                    .append(" | ")
                    .append(meanAndP95(values(questions, u -> toLong(u.inputTokens()))))
                    .append(" | ")
                    .append(meanAndP95(values(questions, u -> toLong(u.outputTokens()))))
                    .append(" | ")
                    .append(costs.isEmpty() ? "—" : total(costs).toPlainString())
                    .append(" | ")
                    .append(costs.isEmpty() ? "—" : mean(costs).toPlainString())
                    .append(" | ")
                    .append(meanAndP95(values(uncached, Usage::latencyMs)))
                    .append(" | ")
                    .append(questions.size() - uncached.size())
                    .append(" |\n");
        }
        md.append("\nCusto estimado pelo preço configurado, não é a fatura. Respostas em cache");
        md.append(" mantêm os tokens e o custo da chamada original e ficam fora da latência.\n");

        md.append("\n## Auditoria humana\n\n");
        md.append("Para cada resposta, confira se está correta e se os trechos citados sustentam");
        md.append(" o que ela afirma (`08` §4); marque as caixas e anote o que achar.\n");
        for (CorpusRun c : run.corpora()) {
            md.append("\n### Corpus ").append(c.corpus().name()).append('\n');
            for (QuestionRun q : c.questions()) {
                audit(md, q);
            }
        }
        return md.toString();
    }

    private static void qualityTable(
            StringBuilder md,
            String header,
            Run run,
            List<Map.Entry<String, Predicate<QuestionRun>>> rows) {
        md.append("| ").append(header);
        md.append(" | Perguntas | Respondidas | Falhas | Validade de citação | Sustentadas |");
        md.append(" Citação relevante | Abstenção correta | Abstenção indevida | Vazamentos |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (var row : rows) {
            Summary s = summarize(questions(run, row.getValue()));
            md.append("| ")
                    .append(row.getKey())
                    .append(" | ")
                    .append(s.questions())
                    .append(" | ")
                    .append(s.answered())
                    .append(" | ")
                    .append(s.failed())
                    .append(" | ")
                    .append(rate(s.validCitations(), s.citations()))
                    .append(" | ")
                    .append(rate(s.supported(), s.answered()))
                    .append(" | ")
                    .append(rate(s.relevant(), s.relevanceMeasured()))
                    .append(" | ")
                    .append(rate(s.correctAbstentions(), s.mustAbstain()))
                    .append(" | ")
                    .append(rate(s.undueAbstentions(), s.shouldAnswer()))
                    .append(" | ")
                    .append(s.leaks())
                    .append(" |\n");
        }
    }

    private static void audit(StringBuilder md, QuestionRun run) {
        Question q = run.question();
        Answer a = run.answer();
        QuestionMetrics m = AnswerQuality.of(q, a);
        md.append("\n#### ")
                .append(q.id())
                .append(" · ")
                .append(q.category())
                .append(" · ")
                .append(q.language())
                .append("\n\n**Pergunta:** ")
                .append(q.question())
                .append("\n\n**Esperado:** ");
        if (q.mustAbstain()) {
            md.append("abstenção");
        } else {
            md.append(
                    String.join(
                            ", ",
                            q.expected().stream().map(AnswerEvaluationReport::expected).toList()));
        }
        md.append("\n\n**Status:** `").append(a.status()).append('`');
        if (a.errorCode() != null) {
            md.append(" (`").append(a.errorCode()).append("`)");
        }
        md.append(" · citações válidas: ")
                .append(m.validCitations())
                .append(" de ")
                .append(m.citations());
        if (m.relevantCitation() != null) {
            md.append(" · citação relevante: ").append(m.relevantCitation() ? "sim" : "não");
        }
        md.append('\n');
        if (!m.leaks().isEmpty()) {
            md.append("\n**Vazamento:** ")
                    .append(String.join(", ", m.leaks().stream().map(t -> "`" + t + "`").toList()))
                    .append('\n');
        }
        if (a.text() != null) {
            md.append("\n**Resposta:**\n\n");
            for (String line : a.text().split("\r?\n", -1)) {
                md.append(line.isEmpty() ? ">" : "> " + line).append('\n');
            }
        }
        List<Evidence> cited = a.evidence().stream().filter(Evidence::cited).toList();
        if (!cited.isEmpty()) {
            md.append("\n**Trechos citados:**\n");
            for (Evidence e : cited) {
                md.append("\n`")
                        .append(e.label())
                        .append("` `")
                        .append(e.paths().getFirst())
                        .append(':')
                        .append(e.citedStart())
                        .append('-')
                        .append(e.citedEnd())
                        .append("`\n\n");
                String snippet = citedLines(e);
                String fence = fence(snippet);
                md.append(fence).append('\n').append(snippet).append('\n').append(fence);
                md.append('\n');
            }
        }
        md.append("\n- [ ] Correta\n- [ ] Sustentada pelas citações\n- Nota:\n");
    }

    // The snippet holds the lines startLine..endLine of the evidence; only the cited ones are
    // shown, numbered as in the prompt.
    static String citedLines(Evidence evidence) {
        String content = evidence.snippet();
        if (content.endsWith("\n")) {
            content = content.substring(0, content.length() - 1);
        }
        String[] lines = content.split("\r?\n", -1);
        StringBuilder out = new StringBuilder();
        for (int number = evidence.citedStart(); number <= evidence.citedEnd(); number++) {
            int index = number - evidence.startLine();
            if (index < 0 || index >= lines.length) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(number).append("| ").append(lines[index]);
        }
        return out.toString();
    }

    // Longer than any run of backticks in the content, so a snippet cannot close the block.
    static String fence(String content) {
        int longest = 0;
        Matcher run = BACKTICKS.matcher(content);
        while (run.find()) {
            longest = Math.max(longest, run.group().length());
        }
        return "`".repeat(Math.max(3, longest + 1));
    }

    private static String expected(ExpectedRange range) {
        return "`" + range.path() + ":" + range.startLine() + "-" + range.endLine() + "`";
    }

    private static Summary summarize(List<QuestionRun> questions) {
        return AnswerQuality.summarize(
                questions.stream().map(q -> AnswerQuality.of(q.question(), q.answer())).toList());
    }

    private static List<QuestionRun> questions(Run run, Predicate<QuestionRun> filter) {
        return run.corpora().stream().flatMap(c -> c.questions().stream()).filter(filter).toList();
    }

    private static List<String> groups(Run run, Function<QuestionRun, String> key) {
        return questions(run, q -> true).stream().map(key).distinct().sorted().toList();
    }

    private static Function<QuestionRun, String> corpusName(Run run) {
        return q ->
                run.corpora().stream()
                        .filter(c -> c.questions().contains(q))
                        .findFirst()
                        .orElseThrow()
                        .corpus()
                        .name();
    }

    private static List<Long> values(
            List<QuestionRun> questions, Function<Usage, @Nullable Long> value) {
        List<Long> values = new ArrayList<>();
        for (QuestionRun q : questions) {
            Long v = value.apply(q.usage());
            if (v != null) {
                values.add(v);
            }
        }
        return values;
    }

    private static @Nullable Long toLong(@Nullable Integer value) {
        return value == null ? null : value.longValue();
    }

    private static String meanAndP95(List<Long> values) {
        if (values.isEmpty()) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.1f", meanOf(values))
                + " / "
                + EvaluationReport.p95(values);
    }

    private static String rate(int count, int total) {
        if (total == 0) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.3f (%d/%d)", (double) count / total, count, total);
    }

    private static BigDecimal total(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        return total(values)
                .divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private static double meanOf(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private static @Nullable Double round(@Nullable Double value) {
        return value == null ? null : Math.round(value * 10_000) / 10_000.0;
    }
}
