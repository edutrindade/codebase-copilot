package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.eval.PerformanceReport.IndexRun;
import com.codebasecopilot.eval.PerformanceReport.Latency;
import com.codebasecopilot.eval.PerformanceReport.Memory;
import com.codebasecopilot.eval.PerformanceReport.TableSize;
import com.codebasecopilot.eval.PerformanceReport.Target;
import com.codebasecopilot.eval.PerformanceReport.ZipImport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PerformanceReportTest {

    @TempDir Path dir;

    @Test
    void latencyUsesNearestRankPercentiles() {
        Latency latency = Latency.of(LongStream.rangeClosed(1, 20).boxed().toList().reversed());

        assertThat(latency).isEqualTo(new Latency(20, 10.5, 10, 19, 20));
        assertThat(Latency.of(List.of(7L))).isEqualTo(new Latency(1, 7.0, 7, 7, 7));
        assertThatThrownBy(() -> Latency.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sizesUseBinaryUnits() {
        assertThat(PerformanceReport.size(512)).isEqualTo("512 B");
        assertThat(PerformanceReport.size(1536)).isEqualTo("1.5 kB");
        assertThat(PerformanceReport.size(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GB");
    }

    @Test
    void writesJsonAndMarkdown() throws IOException {
        Path json = PerformanceReport.write(dir, run());

        assertThat(json.getFileName().toString()).isEqualTo("2026-10-17-perf.json");
        Map<?, ?> root = JsonMapper.builder().build().readValue(Files.readString(json), Map.class);
        assertThat(root.get("date")).isEqualTo("2026-10-17");
        assertThat(((List<?>) root.get("targets"))).hasSize(1);

        String md = Files.readString(dir.resolve("2026-10-17-perf.md"));
        assertThat(md)
                .contains("# Medição de desempenho — 2026-10-17")
                .contains("- `cpus`: 8")
                .contains("| synthetic | 3000 | 50000 | 100.0 MB | 900 | 25.0 MB em 4000 ms |")
                .contains(
                        "| synthetic | completa | 3000 | 60000 | 500 | 30000 | 9000 | 19000 | 1500 |")
                .contains("| synthetic | sem mudanças | 0 | 800 | 400 | 300 | — | — | 100 |")
                .contains("| synthetic | HYBRID | 2 | 15.0 | 10 | 20 | 20 |")
                .contains("| synthetic | pergunta (sem provedor) | 1 | 40.0 | 40 | 40 | 40 |")
                .contains("| synthetic | 512.0 MB | 300.0 MB | 900.0 MB |")
                .contains("| synthetic | completa | 50000 / 2.0 GB (índices 512.0 MB) | 2.0 GB |");
    }

    private static PerformanceReport.Run run() {
        Map<String, Long> full = new LinkedHashMap<>();
        full.put("SCANNING", 500L);
        full.put("PROCESSING_FILES", 30_000L);
        full.put("LEXICAL", 9_000L);
        full.put("EMBEDDING", 19_000L);
        full.put("FINALIZING", 1_500L);
        Map<String, Long> unchanged = new LinkedHashMap<>();
        unchanged.put("SCANNING", 400L);
        unchanged.put("PROCESSING_FILES", 300L);
        unchanged.put("FINALIZING", 100L);
        long gb = 1024L * 1024 * 1024;
        Map<String, Latency> search = new LinkedHashMap<>();
        search.put("HYBRID", Latency.of(List.of(10L, 20L)));
        Target target =
                new Target(
                        "synthetic",
                        3_000,
                        50_000,
                        100L * 1024 * 1024,
                        900,
                        new ZipImport(25L * 1024 * 1024, 4_000),
                        List.of(
                                new IndexRun(
                                        "completa",
                                        3_000,
                                        60_000,
                                        full,
                                        Map.of("chunks", new TableSize(50_000, 2 * gb, gb / 2))),
                                new IndexRun("sem mudanças", 0, 800, unchanged, Map.of())),
                        search,
                        Latency.of(List.of(40L)),
                        new Memory(512L * 1024 * 1024, 300L * 1024 * 1024, 900L * 1024 * 1024));
        return new PerformanceReport.Run(
                "2026-10-17", Map.of("cpus", 8), Map.of(), List.of(target));
    }
}
