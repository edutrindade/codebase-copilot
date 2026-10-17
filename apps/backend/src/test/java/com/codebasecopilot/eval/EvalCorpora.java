package com.codebasecopilot.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Imports and indexes the evaluation corpora through the real pipeline (D-062), for the
// retrieval and answer evaluations. Nothing of the corpus is executed.
public final class EvalCorpora {

    public static final Path REPOSITORY_ROOT = Path.of("../..").toAbsolutePath().normalize();
    public static final Path QUESTIONS = REPOSITORY_ROOT.resolve("eval/questions");
    public static final Path RESULTS = REPOSITORY_ROOT.resolve("eval/results");

    private static final Duration INDEX_TIMEOUT = Duration.ofMinutes(30);
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED");

    private final MockMvcTester mvc;
    private final JdbcClient jdbc;
    private final List<Path> trees = new ArrayList<>();

    public EvalCorpora(MockMvcTester mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    public record Indexed(
            UUID projectId,
            UUID snapshotId,
            String manifestSha256,
            String chunkingVersion,
            int files,
            int chunks) {}

    // EVAL_CORPORA=fixture,click limits the corpora; all by default.
    public static List<Path> questionFiles() throws IOException {
        String selected = System.getenv("EVAL_CORPORA");
        List<String> names =
                selected == null || selected.isBlank()
                        ? null
                        : Arrays.stream(selected.split(",")).map(String::strip).toList();
        List<Path> files;
        try (Stream<Path> list = Files.list(QUESTIONS)) {
            files = list.filter(f -> f.toString().endsWith(".yaml")).sorted().toList();
        }
        if (names == null) {
            return files;
        }
        List<Path> chosen =
                files.stream()
                        .filter(
                                f ->
                                        names.contains(
                                                f.getFileName().toString().replace(".yaml", "")))
                        .toList();
        assertThat(chosen).as("corpora in EVAL_CORPORA").hasSize(names.size());
        return chosen;
    }

    public UUID workspace() {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Avaliação\"}")
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(read(result, "$.id"));
    }

    public Indexed index(QuestionSet set, UUID workspaceId) throws IOException {
        Path source = set.verifiedDirectory(REPOSITORY_ROOT, System::getenv);
        Path tree = copy(source, set.corpus().name());
        UUID projectId = project(workspaceId, tree);
        UUID snapshotId = indexJob(projectId);
        String manifestSha256 = sha256(REPOSITORY_ROOT.resolve(set.corpus().manifest()));
        return jdbc.sql(
                        """
                        SELECT s.chunking_version,
                               (SELECT count(*) FROM snapshot_files f
                                WHERE f.snapshot_id = s.id AND f.content_hash IS NOT NULL)
                                   AS files,
                               (SELECT count(DISTINCT c.id)
                                FROM snapshot_files f
                                JOIN chunks c ON c.project_id = f.project_id
                                             AND c.content_hash = f.content_hash
                                             AND c.chunking_version = s.chunking_version
                                WHERE f.snapshot_id = s.id) AS chunks
                        FROM snapshots s WHERE s.id = :id
                        """)
                .param("id", snapshotId)
                .query(
                        (rs, rowNum) ->
                                new Indexed(
                                        projectId,
                                        snapshotId,
                                        manifestSha256,
                                        rs.getString("chunking_version"),
                                        rs.getInt("files"),
                                        rs.getInt("chunks")))
                .single();
    }

    public @Nullable ModelInfo snapshotModel(UUID snapshotId) {
        return jdbc.sql(
                        """
                        SELECT embedding_provider, embedding_model, embedding_dimensions
                        FROM snapshots WHERE id = :id
                        """)
                .param("id", snapshotId)
                .query(
                        (rs, rowNum) ->
                                rs.getString("embedding_provider") == null
                                        ? null
                                        : new ModelInfo(
                                                rs.getString("embedding_provider"),
                                                rs.getString("embedding_model"),
                                                rs.getInt("embedding_dimensions")))
                .single();
    }

    public void removeTrees() throws IOException {
        for (Path tree : trees) {
            Directories.deleteRecursively(tree);
        }
        trees.clear();
    }

    public static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    // The corpus is copied into a local root, which is where the importer reads folders from.
    private Path copy(Path source, String name) throws IOException {
        Path tree =
                TestFileSystemInitializer.ROOTS_DIR.resolve(
                        "work/eval-" + name + "-" + UUID.randomUUID());
        trees.add(tree);
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

    private UUID project(UUID workspaceId, Path tree) {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/workspaces/{id}/projects", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name": "%s", "sourceType": "LOCAL_FOLDER", "rootKey": "work",
                                 "relativePath": "%s"}
                                """
                                        .formatted(tree.getFileName(), tree.getFileName()))
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(read(result, "$.id"));
    }

    private UUID indexJob(UUID projectId) {
        MvcTestResult created =
                mvc.post().uri("/api/v1/projects/{id}/index-jobs", projectId).exchange();
        assertThat(created).hasStatus(HttpStatus.ACCEPTED);
        String jobId = read(created, "$.id");
        String status =
                await().atMost(INDEX_TIMEOUT)
                        .pollInterval(Duration.ofMillis(500))
                        .until(
                                () ->
                                        read(
                                                mvc.get()
                                                        .uri("/api/v1/index-jobs/{id}", jobId)
                                                        .exchange(),
                                                "$.status"),
                                TERMINAL::contains);
        assertThat(status).as("index job %s", jobId).isEqualTo("COMPLETED");
        return UUID.fromString(read(created, "$.snapshotId"));
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
