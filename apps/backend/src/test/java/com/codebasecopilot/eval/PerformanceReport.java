package com.codebasecopilot.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

// Results of one performance run (P6 of Etapa 8) as JSON and a Markdown summary in pt-BR. Holds
// only sizes, counts and durations: no path, content or question of the measured trees.
public final class PerformanceReport {

    public record Run(
            String date,
            Map<String, Object> environment,
            Map<String, Object> config,
            List<Target> targets) {}

    public record Target(
            String name,
            int files,
            int chunks,
            long sourceBytes,
            long scanMillis,
            @Nullable ZipImport zipImport,
            List<IndexRun> indexRuns,
            Map<String, Latency> searchMillis,
            @Nullable Latency questionMillis,
            Memory memory) {}

    public record ZipImport(long zipBytes, long millis) {}

    // label: what the run indexed (full, no changes, 10% changed…); storage is measured after it.
    public record IndexRun(
            String label,
            int changedFiles,
            long totalMillis,
            Map<String, Long> stageMillis,
            Map<String, TableSize> storage) {}

    // bytes: table, indexes and TOAST; indexBytes: the indexes alone.
    public record TableSize(long rows, long bytes, long indexBytes) {}

    public record Memory(long heapMaxBytes, long heapPeakBytes, @Nullable Long rssPeakBytes) {}

    public record Latency(int runs, double mean, long p50, long p95, long max) {

        // Nearest-rank percentiles.
        public static Latency of(List<Long> millis) {
            if (millis.isEmpty()) {
                throw new IllegalArgumentException("no measurements");
            }
            List<Long> sorted = millis.stream().sorted().toList();
            return new Latency(
                    sorted.size(),
                    sorted.stream().mapToLong(Long::longValue).average().orElseThrow(),
                    rank(sorted, 0.50),
                    rank(sorted, 0.95),
                    sorted.getLast());
        }

        private static long rank(List<Long> sorted, double percentile) {
            return sorted.get((int) Math.ceil(percentile * sorted.size()) - 1);
        }
    }

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final List<String> STAGES =
            List.of("SCANNING", "PROCESSING_FILES", "LEXICAL", "EMBEDDING", "FINALIZING");

    private PerformanceReport() {}

    // Writes <date>-perf.json and .md and returns the JSON file.
    public static Path write(Path directory, Run run) throws IOException {
        Files.createDirectories(directory);
        Path json = directory.resolve(run.date() + "-perf.json");
        Files.writeString(json, JSON.writeValueAsString(run) + "\n", StandardCharsets.UTF_8);
        Files.writeString(
                directory.resolve(run.date() + "-perf.md"), markdown(run), StandardCharsets.UTF_8);
        return json;
    }

    public static String markdown(Run run) {
        StringBuilder md = new StringBuilder();
        md.append("# Medição de desempenho — ")
                .append(run.date())
                .append(
                        "\n\nGerado pelo `PerformanceEvaluationIT`; os valores exatos estão no JSON"
                                + " de mesmo nome.\n\n## Ambiente\n\n");
        run.environment()
                .forEach((k, v) -> md.append("- `").append(k).append("`: ").append(v).append('\n'));
        md.append("\n## Configuração\n\n");
        run.config()
                .forEach((k, v) -> md.append("- `").append(k).append("`: ").append(v).append('\n'));

        md.append(
                "\n## Árvores medidas\n\n| Árvore | Arquivos | Chunks | Tamanho | Varredura (ms) |"
                        + " Import ZIP |\n|---|---|---|---|---|---|\n");
        for (Target t : run.targets()) {
            md.append("| ")
                    .append(t.name())
                    .append(" | ")
                    .append(t.files())
                    .append(" | ")
                    .append(t.chunks())
                    .append(" | ")
                    .append(size(t.sourceBytes()))
                    .append(" | ")
                    .append(t.scanMillis())
                    .append(" | ")
                    .append(
                            t.zipImport() == null
                                    ? "—"
                                    : size(t.zipImport().zipBytes())
                                            + " em "
                                            + t.zipImport().millis()
                                            + " ms")
                    .append(" |\n");
        }

        md.append("\n## Indexação (ms)\n\n| Árvore | Execução | Arquivos alterados | Total |");
        for (String stage : STAGES) {
            md.append(' ').append(stage).append(" |");
        }
        md.append("\n|---|---|---|---|").append("---|".repeat(STAGES.size())).append('\n');
        for (Target t : run.targets()) {
            for (IndexRun r : t.indexRuns()) {
                md.append("| ")
                        .append(t.name())
                        .append(" | ")
                        .append(r.label())
                        .append(" | ")
                        .append(r.changedFiles())
                        .append(" | ")
                        .append(r.totalMillis())
                        .append(" |");
                for (String stage : STAGES) {
                    Long millis = r.stageMillis().get(stage);
                    md.append(' ').append(millis == null ? "—" : millis).append(" |");
                }
                md.append('\n');
            }
        }

        md.append(
                "\n## Busca e pergunta (ms)\n\n| Árvore | Operação | Execuções | Média | p50 | p95 |"
                        + " Máximo |\n|---|---|---|---|---|---|---|\n");
        for (Target t : run.targets()) {
            t.searchMillis().forEach((mode, latency) -> latency(md, t.name(), mode, latency));
            if (t.questionMillis() != null) {
                latency(md, t.name(), "pergunta (sem provedor)", t.questionMillis());
            }
        }

        md.append(
                "\n## Memória\n\n| Árvore | Heap máximo | Pico de heap | Pico de RSS |\n"
                        + "|---|---|---|---|\n");
        for (Target t : run.targets()) {
            md.append("| ")
                    .append(t.name())
                    .append(" | ")
                    .append(size(t.memory().heapMaxBytes()))
                    .append(" | ")
                    .append(size(t.memory().heapPeakBytes()))
                    .append(" | ")
                    .append(
                            t.memory().rssPeakBytes() == null
                                    ? "—"
                                    : size(t.memory().rssPeakBytes()))
                    .append(" |\n");
        }

        Set<String> tables = new LinkedHashSet<>();
        run.targets().forEach(t -> t.indexRuns().forEach(r -> tables.addAll(r.storage().keySet())));
        md.append(
                "\n## Armazenamento depois de cada execução (linhas / tamanho)\n\n| Árvore | Execução |");
        for (String table : tables) {
            md.append(" `").append(table).append("` |");
        }
        md.append(" Total |\n|---|---|").append("---|".repeat(tables.size() + 1)).append('\n');
        for (Target t : run.targets()) {
            for (IndexRun r : t.indexRuns()) {
                md.append("| ").append(t.name()).append(" | ").append(r.label()).append(" |");
                long total = 0;
                for (String table : tables) {
                    TableSize s = r.storage().get(table);
                    md.append(' ')
                            .append(
                                    s == null
                                            ? "—"
                                            : s.rows()
                                                    + " / "
                                                    + size(s.bytes())
                                                    + " (índices "
                                                    + size(s.indexBytes())
                                                    + ")")
                            .append(" |");
                    total += s == null ? 0 : s.bytes();
                }
                md.append(' ').append(size(total)).append(" |\n");
            }
        }
        md.append(
                "\nOs tamanhos são de `pg_total_relation_size` (tabela, índices e TOAST) com o banco"
                        + " só com a árvore medida; linhas apagadas pela poda só liberam espaço"
                        + " depois do autovacuum. A latência varia entre execuções.\n");
        return md.toString();
    }

    private static void latency(StringBuilder md, String target, String what, Latency l) {
        md.append("| ")
                .append(target)
                .append(" | ")
                .append(what)
                .append(" | ")
                .append(l.runs())
                .append(" | ")
                .append(String.format(Locale.ROOT, "%.1f", l.mean()))
                .append(" | ")
                .append(l.p50())
                .append(" | ")
                .append(l.p95())
                .append(" | ")
                .append(l.max())
                .append(" |\n");
    }

    static String size(long bytes) {
        List<String> units = new ArrayList<>(List.of("B", "kB", "MB", "GB"));
        double value = bytes;
        String unit = units.removeFirst();
        while (value >= 1024 && !units.isEmpty()) {
            value /= 1024;
            unit = units.removeFirst();
        }
        return unit.equals("B") ? bytes + " B" : String.format(Locale.ROOT, "%.1f %s", value, unit);
    }
}
