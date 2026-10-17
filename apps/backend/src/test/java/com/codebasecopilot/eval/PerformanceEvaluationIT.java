package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.TestProperties;
import com.codebasecopilot.TestcontainersConfiguration;
import com.codebasecopilot.eval.PerformanceReport.IndexRun;
import com.codebasecopilot.eval.PerformanceReport.Latency;
import com.codebasecopilot.eval.PerformanceReport.Memory;
import com.codebasecopilot.eval.PerformanceReport.TableSize;
import com.codebasecopilot.eval.PerformanceReport.Target;
import com.codebasecopilot.eval.PerformanceReport.ZipImport;
import com.codebasecopilot.shared.fs.Directories;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Performance harness (P6 of Etapa 8), outside ./mvnw verify. Measures, through the API and with
// the fake providers, the scan, the ZIP import, each indexing stage (full, without changes and
// after rewriting 10% of the files), the search in each mode, the question without the provider
// time, heap and RSS, and the storage of each table. Runs on the fixture, on each external corpus
// whose folder is set (eval/README.md) and on a generated tree of about 50,000 chunks; writes
// eval/results/<date>-perf.json and .md (EVAL_RESULTS_DIR replaces the folder;
// EVAL_SYNTHETIC_FILES shrinks the generated tree, to check the harness). Nothing of the
// trees is executed, and the results hold no path, content or question.
// ./mvnw verify -Dtest.excluded-groups= -Dgroups=performance -Dtest=none
//     -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=PerformanceEvaluationIT
@Tag("performance")
@SpringBootTest(
        properties = {
            TestProperties.CONFIG_LOCATION,
            TestProperties.NO_PROJECTS_ROOT,
            TestProperties.DEFAULT_ROOT_KEY,
            TestProperties.DEFAULT_ROOT_LABEL,
            TestProperties.DEFAULT_ROOT_PATH,
            TestProperties.WORK_ROOT_KEY,
            TestProperties.WORK_ROOT_LABEL,
            TestProperties.WORK_ROOT_PATH,
            TestProperties.INDEX_POLL_INTERVAL,
            TestProperties.EMBEDDING_PROVIDER,
            TestProperties.NO_EMBEDDING_API_KEY,
            TestProperties.GENERATION_PROVIDER,
            TestProperties.NO_GENERATION_API_KEY,
            // The synthetic tree is at the default limit of 50,000; the measurement says
            // whether the limit should move (P6).
            PerformanceEvaluationIT.MAX_CHUNKS_PER_JOB
        })
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestFileSystemInitializer.class)
@Import(TestcontainersConfiguration.class)
class PerformanceEvaluationIT {

    static final String MAX_CHUNKS_PER_JOB = "app.embeddings.max-chunks-per-job=200000";

    private static final int WARMUP = 5;
    private static final int SEARCH_RUNS = 50;
    private static final int QUESTION_RUNS = 20;
    private static final int MUTATION_ROUNDS = 3;
    private static final double MUTATED_FRACTION = 0.1;
    private static final List<String> MODES = List.of("LEXICAL", "VECTOR", "HYBRID");
    private static final List<String> TABLES =
            List.of(
                    "file_contents",
                    "snapshot_files",
                    "chunks",
                    "chunk_lexical",
                    "chunk_embeddings");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

    private final List<Path> trees = new ArrayList<>();

    @AfterEach
    void clean() throws IOException {
        for (Path tree : trees) {
            Directories.deleteRecursively(tree);
        }
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void measuresTheCorporaAndASyntheticTree() throws IOException {
        List<Target> targets = new ArrayList<>();
        for (Path file : EvalCorpora.questionFiles()) {
            QuestionSet set = QuestionSets.load(file);
            String env = set.corpus().directoryEnv();
            if (env != null && System.getenv(env) == null) {
                System.out.println(
                        "Skipping corpus " + set.corpus().name() + ": " + env + " not set");
                continue;
            }
            Path tree = copy(set.verifiedDirectory(EvalCorpora.REPOSITORY_ROOT, System::getenv));
            List<String> questions =
                    set.questions().stream().map(QuestionSet.Question::question).toList();
            targets.add(measure(set.corpus().name(), tree, questions, List.of(), false, null));
        }

        SyntheticRepository.Spec spec = SyntheticRepository.Spec.DEFAULT;
        String files = System.getenv("EVAL_SYNTHETIC_FILES");
        if (files != null && !files.isBlank()) {
            spec =
                    new SyntheticRepository.Spec(
                            Integer.parseInt(files.strip()),
                            spec.minLines(),
                            spec.maxLines(),
                            spec.vocabulary(),
                            spec.seed());
        }
        Path tree = newTree("synthetic");
        SyntheticRepository.write(tree, spec);
        targets.add(
                measure(
                        "synthetic",
                        tree,
                        SyntheticRepository.questions(spec, SEARCH_RUNS),
                        SyntheticRepository.commonTermQuestions(spec, SEARCH_RUNS),
                        true,
                        spec));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("embeddings", "fake, 1536 dimensões (só o custo local)");
        config.put("generation", "fake (sem o tempo do provedor)");
        config.put("maxChunksPerJob", 200_000);
        config.put("searchRuns", SEARCH_RUNS + " (mais " + WARMUP + " de aquecimento)");
        config.put("questionRuns", QUESTION_RUNS);
        config.put("searchLimit", 10);
        config.put(
                "synthetic",
                spec.files()
                        + " arquivos de "
                        + spec.minLines()
                        + " a "
                        + spec.maxLines()
                        + " linhas, vocabulário de "
                        + spec.vocabulary()
                        + ", semente "
                        + spec.seed());
        config.put(
                "mutation",
                MUTATION_ROUNDS + " rodadas de " + (int) (MUTATED_FRACTION * 100) + "%");
        PerformanceReport.Run run =
                new PerformanceReport.Run(
                        LocalDate.now().toString(), environment(), config, targets);
        String dir = System.getenv("EVAL_RESULTS_DIR");
        Path written =
                PerformanceReport.write(
                        dir == null || dir.isBlank() ? EvalCorpora.RESULTS : Path.of(dir), run);
        System.out.println("Performance results written to " + written);
    }

    private Target measure(
            String name,
            Path tree,
            List<String> questions,
            List<String> commonTermQuestions,
            boolean withZip,
            SyntheticRepository.@Nullable Spec mutable)
            throws IOException {
        DatabaseCleaner.truncateAll(jdbc);
        UUID workspaceId = workspace();
        UUID projectId = localProject(workspaceId, tree);
        long sourceBytes = sourceBytes(tree);

        long scanMillis =
                millis(
                        () ->
                                assertThat(
                                                mvc.get()
                                                        .uri(
                                                                "/api/v1/projects/{id}/inventory",
                                                                projectId)
                                                        .exchange())
                                        .hasStatusOk());
        ZipImport zip = withZip ? zipImport(workspaceId, tree) : null;

        MemorySampler memory = new MemorySampler();
        memory.start();
        List<IndexRun> runs = new ArrayList<>();
        UUID snapshotId;
        try {
            int files = fileCount(tree);
            runs.add(index(projectId, "completa", files));
            runs.add(index(projectId, "sem mudanças", 0));
            if (mutable != null) {
                for (int round = 0; round < MUTATION_ROUNDS; round++) {
                    int changed =
                            SyntheticRepository.mutate(tree, mutable, round, MUTATED_FRACTION)
                                    .size();
                    runs.add(index(projectId, "10% alterados, rodada " + (round + 1), changed));
                }
            }
            snapshotId = activeSnapshot(projectId);

            Map<String, Latency> search = new LinkedHashMap<>();
            for (String mode : MODES) {
                search.put(mode, search(projectId, mode, questions));
            }
            if (!commonTermQuestions.isEmpty()) {
                search.put(
                        "LEXICAL, termo comum", search(projectId, "LEXICAL", commonTermQuestions));
                search.put("HYBRID, termo comum", search(projectId, "HYBRID", commonTermQuestions));
            }
            Latency question = question(projectId, questions);
            int[] counts = counts(snapshotId);
            return new Target(
                    name,
                    counts[0],
                    counts[1],
                    sourceBytes,
                    scanMillis,
                    zip,
                    runs,
                    search,
                    question,
                    memory.stop());
        } finally {
            memory.stop();
        }
    }

    private IndexRun index(UUID projectId, String label, int changedFiles) {
        long start = System.nanoTime();
        MvcTestResult created =
                mvc.post().uri("/api/v1/projects/{id}/index-jobs", projectId).exchange();
        assertThat(created).hasStatus(HttpStatus.ACCEPTED);
        UUID jobId = UUID.fromString(EvalCorpora.read(created, "$.id"));

        Map<String, Long> stages = new LinkedHashMap<>();
        String stage = null;
        long stageStart = 0;
        String status;
        while (true) {
            Map<String, Object> row =
                    jdbc.sql("SELECT status, stage FROM index_jobs WHERE id = :id")
                            .param("id", jobId)
                            .query()
                            .singleRow();
            status = (String) row.get("status");
            String current = (String) row.get("stage");
            long now = System.nanoTime();
            if (current != null && !current.equals(stage)) {
                if (stage != null) {
                    stages.merge(stage, (now - stageStart) / 1_000_000, Long::sum);
                }
                stage = current;
                stageStart = now;
            }
            if (status.equals("COMPLETED") || status.equals("FAILED")) {
                if (stage != null) {
                    stages.merge(stage, (now - stageStart) / 1_000_000, Long::sum);
                }
                break;
            }
            pause();
        }
        long total = (System.nanoTime() - start) / 1_000_000;
        assertThat(status).as("index job %s (%s)", jobId, label).isEqualTo("COMPLETED");
        return new IndexRun(label, changedFiles, total, stages, storage());
    }

    private Latency search(UUID projectId, String mode, List<String> queries) {
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < WARMUP + SEARCH_RUNS; i++) {
            String query = queries.get(i % queries.size());
            long elapsed =
                    millis(
                            () ->
                                    assertThat(
                                                    mvc.post()
                                                            .uri(
                                                                    "/api/v1/projects/{id}/search",
                                                                    projectId)
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .content(
                                                                    json(
                                                                            Map.of(
                                                                                    "query", query,
                                                                                    "mode", mode,
                                                                                    "limit", 10)))
                                                            .exchange())
                                            .hasStatusOk());
            if (i >= WARMUP) {
                millis.add(elapsed);
            }
        }
        return Latency.of(millis);
    }

    private Latency question(UUID projectId, List<String> questions) {
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < QUESTION_RUNS; i++) {
            String question = questions.get(i % questions.size());
            millis.add(
                    millis(
                            () ->
                                    assertThat(
                                                    mvc.post()
                                                            .uri(
                                                                    "/api/v1/projects/{id}/questions",
                                                                    projectId)
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .content(
                                                                    json(
                                                                            Map.of(
                                                                                    "question",
                                                                                    question)))
                                                            .exchange())
                                            .hasStatus(HttpStatus.CREATED)));
        }
        return Latency.of(millis);
    }

    private ZipImport zipImport(UUID workspaceId, Path tree) throws IOException {
        byte[] archive = zip(tree);
        MvcTestResult created =
                mvc.post()
                        .uri("/api/v1/workspaces/{id}/projects", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Medição ZIP\", \"sourceType\": \"ZIP\"}")
                        .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        UUID projectId = UUID.fromString(EvalCorpora.read(created, "$.id"));

        long start = System.nanoTime();
        MvcTestResult upload =
                mvc.post()
                        .uri("/api/v1/projects/{id}/imports/zip", projectId)
                        .multipart()
                        .file(new MockMultipartFile("file", "repo.zip", "application/zip", archive))
                        .exchange();
        assertThat(upload).hasStatus(HttpStatus.ACCEPTED);
        String importId = EvalCorpora.read(upload, "$.id");
        String status;
        do {
            pause();
            status =
                    EvalCorpora.read(
                            mvc.get().uri("/api/v1/imports/{id}", importId).exchange(), "$.status");
        } while (!List.of("COMPLETED", "REJECTED", "FAILED").contains(status));
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertThat(status).as("ZIP import").isEqualTo("COMPLETED");
        assertThat(mvc.delete().uri("/api/v1/projects/{id}", projectId).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
        return new ZipImport(archive.length, millis);
    }

    private Map<String, TableSize> storage() {
        Map<String, TableSize> sizes = new LinkedHashMap<>();
        for (String table : TABLES) {
            // Table names come from the fixed list above.
            sizes.put(
                    table,
                    jdbc.sql(
                                    "SELECT count(*) AS rows, pg_total_relation_size('%1$s') AS bytes,"
                                                    .formatted(table)
                                            + " pg_indexes_size('%1$s') AS index_bytes FROM %1$s"
                                                    .formatted(table))
                            .query(
                                    (rs, rowNum) ->
                                            new TableSize(
                                                    rs.getLong("rows"),
                                                    rs.getLong("bytes"),
                                                    rs.getLong("index_bytes")))
                            .single());
        }
        return sizes;
    }

    private UUID activeSnapshot(UUID projectId) {
        return jdbc.sql(
                        """
                        SELECT snapshot_id FROM index_jobs
                        WHERE project_id = :id AND status = 'COMPLETED'
                        ORDER BY completed_at DESC LIMIT 1
                        """)
                .param("id", projectId)
                .query(UUID.class)
                .single();
    }

    // Same counts as EvalCorpora: indexed files and distinct chunks of the snapshot.
    private int[] counts(UUID snapshotId) {
        return jdbc.sql(
                        """
                        SELECT (SELECT count(*) FROM snapshot_files f
                                WHERE f.snapshot_id = s.id AND f.content_hash IS NOT NULL) AS files,
                               (SELECT count(DISTINCT c.id)
                                FROM snapshot_files f
                                JOIN chunks c ON c.project_id = f.project_id
                                             AND c.content_hash = f.content_hash
                                             AND c.chunking_version = s.chunking_version
                                WHERE f.snapshot_id = s.id) AS chunks
                        FROM snapshots s WHERE s.id = :id
                        """)
                .param("id", snapshotId)
                .query((rs, rowNum) -> new int[] {rs.getInt("files"), rs.getInt("chunks")})
                .single();
    }

    private Map<String, Object> environment() {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        env.put("arch", System.getProperty("os.arch"));
        env.put("cpus", Runtime.getRuntime().availableProcessors());
        if (ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean os) {
            env.put("memory", PerformanceReport.size(os.getTotalMemorySize()));
        }
        env.put(
                "java",
                System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        env.put(
                "jvmMemoryArguments",
                ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                        .filter(a -> a.startsWith("-Xm") || a.startsWith("-XX:MaxRAM"))
                        .toList());
        env.put("heapMax", PerformanceReport.size(Runtime.getRuntime().maxMemory()));
        env.put(
                "postgres",
                jdbc.sql("SHOW server_version").query(String.class).single()
                        + " (Testcontainers, pgvector)");
        return env;
    }

    private UUID workspace() {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Medição\"}")
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(EvalCorpora.read(result, "$.id"));
    }

    private UUID localProject(UUID workspaceId, Path tree) {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/workspaces/{id}/projects", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json(
                                        Map.of(
                                                "name",
                                                tree.getFileName().toString(),
                                                "sourceType",
                                                "LOCAL_FOLDER",
                                                "rootKey",
                                                "work",
                                                "relativePath",
                                                tree.getFileName().toString())))
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(EvalCorpora.read(result, "$.id"));
    }

    private Path newTree(String name) throws IOException {
        Path tree =
                TestFileSystemInitializer.ROOTS_DIR.resolve(
                        "work/perf-" + name + "-" + UUID.randomUUID());
        Files.createDirectories(tree);
        trees.add(tree);
        return tree;
    }

    private Path copy(Path source) throws IOException {
        Path tree = newTree("corpus");
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path from : paths.toList()) {
                Path to = tree.resolve(source.relativize(from).toString());
                if (Files.isDirectory(from)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        return tree;
    }

    private static byte[] zip(Path tree) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes);
                Stream<Path> paths = Files.walk(tree)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                out.putNextEntry(new ZipEntry(tree.relativize(file).toString()));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static long sourceBytes(Path tree) throws IOException {
        try (Stream<Path> paths = Files.walk(tree)) {
            long total = 0;
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                total += Files.size(file);
            }
            return total;
        }
    }

    private static int fileCount(Path tree) throws IOException {
        try (Stream<Path> paths = Files.walk(tree)) {
            return (int) paths.filter(Files::isRegularFile).count();
        }
    }

    private static String json(Map<String, Object> fields) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(fields);
    }

    private static long millis(Runnable call) {
        long start = System.nanoTime();
        call.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void pause() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // Heap used, sampled every 50 ms, and the RSS of this JVM (which also holds the test code),
    // every second through ps; the RSS is left empty where ps is not available.
    private static final class MemorySampler {

        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicLong heapPeak = new AtomicLong();
        private final AtomicLong rssPeak = new AtomicLong(-1);
        private @Nullable Thread thread;
        private @Nullable Memory result;

        void start() {
            running.set(true);
            thread =
                    Thread.ofPlatform()
                            .daemon()
                            .start(
                                    () -> {
                                        long lastRss = 0;
                                        while (running.get()) {
                                            heapPeak.accumulateAndGet(
                                                    ManagementFactory.getMemoryMXBean()
                                                            .getHeapMemoryUsage()
                                                            .getUsed(),
                                                    Math::max);
                                            if (System.currentTimeMillis() - lastRss >= 1_000) {
                                                lastRss = System.currentTimeMillis();
                                                Long rss = rss();
                                                if (rss != null) {
                                                    rssPeak.accumulateAndGet(rss, Math::max);
                                                }
                                            }
                                            try {
                                                Thread.sleep(50);
                                            } catch (InterruptedException e) {
                                                return;
                                            }
                                        }
                                    });
        }

        Memory stop() {
            if (result == null) {
                running.set(false);
                if (thread != null) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                result =
                        new Memory(
                                Runtime.getRuntime().maxMemory(),
                                heapPeak.get(),
                                rssPeak.get() < 0 ? null : rssPeak.get());
            }
            return result;
        }

        private static @Nullable Long rss() {
            try {
                Process ps =
                        new ProcessBuilder(
                                        "ps",
                                        "-o",
                                        "rss=",
                                        "-p",
                                        Long.toString(ProcessHandle.current().pid()))
                                .redirectErrorStream(true)
                                .start();
                String out = new String(ps.getInputStream().readAllBytes()).strip();
                return ps.waitFor() == 0 ? Long.parseLong(out) * 1024 : null;
            } catch (IOException | NumberFormatException e) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }
}
