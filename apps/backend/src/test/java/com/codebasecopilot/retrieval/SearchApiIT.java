package com.codebasecopilot.retrieval;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.IndexRows.insertSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.TestFileSystemInitializer;
import com.codebasecopilot.indexing.EmbeddingContext;
import com.codebasecopilot.providers.EmbeddingFailure;
import com.codebasecopilot.providers.EmbeddingFailure.Type;
import com.codebasecopilot.shared.fs.Directories;
import com.codebasecopilot.shared.persistence.VectorLiteral;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Acceptance of Etapa 4: vector queries return chunks of the right snapshot (D-049, D-053).
@ExtendWith(OutputCaptureExtension.class)
class SearchApiIT extends EmbeddingContext {

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED");

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

    private UUID workspaceId;
    private final List<Path> trees = new ArrayList<>();

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        reset(provider);
        workspaceId = insertWorkspace(jdbc);
    }

    @AfterEach
    void removeTrees() throws IOException {
        reset(provider);
        for (Path tree : trees) {
            Directories.deleteRecursively(tree);
        }
    }

    @Test
    void searchesOnlyTheRequestedSnapshotOfTheProject() throws IOException {
        Path tree = tree();
        write(tree, "billing.txt", "invoice payment charge");
        String projectId = project(tree);
        String first = index(projectId);
        write(tree, "billing.txt", "refund ledger");
        String second = index(projectId);
        Path otherTree = tree();
        write(otherTree, "billing.txt", "invoice payment charge refund ledger");
        index(project(otherTree));

        DocumentContext active = json(search(projectId, "{\"query\": \"invoice payment\"}"));
        DocumentContext old =
                json(
                        search(
                                projectId,
                                """
                                {"query": "invoice payment", "snapshotId": "%s"}
                                """
                                        .formatted(first)));

        assertThat((String) active.read("$.snapshotId")).isEqualTo(second);
        assertThat((List<String>) active.read("$.results[*].content"))
                .containsExactly("refund ledger");
        assertThat((String) old.read("$.snapshotId")).isEqualTo(first);
        assertThat((List<String>) old.read("$.results[*].content"))
                .containsExactly("invoice payment charge");
    }

    @Test
    void ranksTheChunkWithTheQueryTermsFirstAndBreaksTiesByPath() throws IOException {
        Path tree = tree();
        write(tree, "c-login.txt", "user login session");
        // Same terms in another order: the fake gives both the same vector.
        write(tree, "b-pay.txt", "charge payment");
        write(tree, "a-pay.txt", "payment charge");
        String projectId = project(tree);
        index(projectId);

        DocumentContext result =
                json(search(projectId, "{\"query\": \"payment charge\", \"limit\": 3}"));

        assertThat((List<String>) result.read("$.results[*].path"))
                .containsExactly("a-pay.txt", "b-pay.txt", "c-login.txt");
        List<Double> scores = result.read("$.results[*].score");
        assertThat(scores.get(0)).isEqualTo(scores.get(1)).isGreaterThan(scores.get(2));
        assertThat(scores.get(0)).isCloseTo(1.0, offset(1e-6));
        assertThat((String) result.read("$.model.provider")).isEqualTo("fake");
        assertThat((Integer) result.read("$.model.dimensions"))
                .isEqualTo(provider.model().dimensions());
        assertThat((Integer) result.read("$.results[0].startLine")).isEqualTo(1);
        assertThat((Integer) result.read("$.results[0].endLine")).isEqualTo(1);
    }

    @Test
    void limitBoundsTheResults() throws IOException {
        Path tree = tree();
        for (int i = 1; i <= 12; i++) {
            write(tree, "f" + i + ".txt", "term" + i);
        }
        String projectId = project(tree);
        index(projectId);

        assertThat((List<?>) json(search(projectId, "{\"query\": \"term1\"}")).read("$.results"))
                .hasSize(SearchService.DEFAULT_LIMIT);
        assertThat(
                        (List<?>)
                                json(search(projectId, "{\"query\": \"term1\", \"limit\": 2}"))
                                        .read("$.results"))
                .hasSize(2);
    }

    @Test
    void sameContentInTwoPathsIsOneResult() throws IOException {
        Path tree = tree();
        write(tree, "src/b.txt", "shared helper");
        write(tree, "src/a.txt", "shared helper");
        write(tree, "z.txt", "shared helper");
        String projectId = project(tree);
        index(projectId);

        DocumentContext result = json(search(projectId, "{\"query\": \"helper\"}"));

        assertThat((List<?>) result.read("$.results")).hasSize(1);
        assertThat((String) result.read("$.results[0].path")).isEqualTo("src/a.txt");
        assertThat((List<String>) result.read("$.results[0].otherPaths"))
                .containsExactly("src/b.txt", "z.txt");
    }

    @Test
    void vectorsOfAnotherModelDoNotInterfere() throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "payment charge");
        write(tree, "b.txt", "user login");
        String projectId = project(tree);
        index(projectId);
        List<String> before =
                json(search(projectId, "{\"query\": \"payment\"}")).read("$.results[*].path");
        float[] queryVector = provider.embed(List.of("payment")).vectors().getFirst();
        // Another model's vector of b.txt equal to the query would rank it first if mixed in.
        jdbc.sql(
                        """
                        INSERT INTO chunk_embeddings (chunk_hash, provider, model, dimensions,
                                                      embedding, created_at)
                        SELECT chunk_hash, 'fake', 'other-model', :dimensions,
                               CAST(:vector AS vector), now()
                        FROM chunks WHERE content = 'user login'
                        """)
                .param("dimensions", queryVector.length)
                .param("vector", VectorLiteral.of(queryVector))
                .update();

        List<String> after =
                json(search(projectId, "{\"query\": \"payment\"}")).read("$.results[*].path");

        assertThat(after).isEqualTo(before).startsWith("a.txt");
    }

    @Test
    void snapshotOfAnotherModelIsAMismatch() throws IOException {
        String projectId = indexedProject();
        jdbc.sql("UPDATE snapshots SET embedding_model = 'other-model'").update();

        assertError(search(projectId, "{\"query\": \"alpha\"}"), "SNAPSHOT_EMBEDDING_MISMATCH");
        verify(provider, never()).embed(any());
    }

    @Test
    void snapshotWithoutEmbeddingsIsAMismatch() throws IOException {
        String projectId = indexedProject();
        jdbc.sql(
                        """
                        UPDATE snapshots SET embedding_provider = NULL, embedding_model = NULL,
                                             embedding_dimensions = NULL, embedding_tokens = NULL
                        """)
                .update();

        assertError(search(projectId, "{\"query\": \"alpha\"}"), "SNAPSHOT_EMBEDDING_MISMATCH");
    }

    @Test
    void prunedSnapshotIsNotSearchable() throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha");
        String projectId = project(tree);
        String first = index(projectId);
        write(tree, "a.txt", "beta");
        index(projectId);
        jdbc.sql("UPDATE snapshots SET pruned_at = now() WHERE id = :id")
                .param("id", UUID.fromString(first))
                .update();

        assertError(
                search(
                        projectId,
                        "{\"query\": \"alpha\", \"snapshotId\": \"%s\"}".formatted(first)),
                "SNAPSHOT_NOT_SEARCHABLE");
    }

    @ParameterizedTest
    @CsvSource({"BUILDING", "FAILED"})
    void unfinishedSnapshotIsNotSearchable(String status) throws IOException {
        String projectId = indexedProject();
        UUID snapshotId = insertSnapshot(jdbc, UUID.fromString(projectId), status);

        assertError(
                search(
                        projectId,
                        "{\"query\": \"alpha\", \"snapshotId\": \"%s\"}".formatted(snapshotId)),
                "SNAPSHOT_NOT_SEARCHABLE");
    }

    @Test
    void projectWithoutActiveSnapshotIsNotSearchable() throws IOException {
        assertError(search(project(tree()), "{\"query\": \"alpha\"}"), "SNAPSHOT_NOT_SEARCHABLE");
    }

    @Test
    void snapshotOfAnotherProjectIsNotFound() throws IOException {
        String projectId = indexedProject();
        Path otherTree = tree();
        write(otherTree, "a.txt", "alpha");
        String otherSnapshot = index(project(otherTree));

        assertError(
                search(
                        projectId,
                        "{\"query\": \"alpha\", \"snapshotId\": \"%s\"}".formatted(otherSnapshot)),
                "SNAPSHOT_NOT_FOUND");
        assertError(
                search(
                        projectId,
                        "{\"query\": \"alpha\", \"snapshotId\": \"%s\"}"
                                .formatted(UUID.randomUUID())),
                "SNAPSHOT_NOT_FOUND");
    }

    @Test
    void unknownProjectIsNotFound() {
        assertError(
                search(UUID.randomUUID().toString(), "{\"query\": \"alpha\"}"),
                "PROJECT_NOT_FOUND");
    }

    @Test
    void invalidQueryAndLimitAreReportedBeforeAnyLookup() {
        MvcTestResult result =
                search(
                        UUID.randomUUID().toString(),
                        "{\"query\": \"%s\", \"limit\": 51}".formatted("x".repeat(2001)));

        assertError(result, "VALIDATION_FAILED");
        assertThat((List<String>) json(result).read("$.errors[*].field"))
                .containsExactlyInAnyOrder("query", "limit");
    }

    @ParameterizedTest
    @CsvSource({"'', 10, query", "'   ', 10, query", "alpha, 0, limit", "alpha, 51, limit"})
    void validatesQueryAndLimit(String query, int limit, String field) {
        MvcTestResult result =
                search(
                        UUID.randomUUID().toString(),
                        "{\"query\": \"%s\", \"limit\": %d}".formatted(query, limit));

        assertError(result, "VALIDATION_FAILED");
        assertThat((List<String>) json(result).read("$.errors[*].field")).containsExactly(field);
    }

    @Test
    void queryAtTheMaximumLengthIsAccepted() throws IOException {
        String projectId = indexedProject();

        assertThat(search(projectId, "{\"query\": \"%s\"}".formatted("x".repeat(2000))))
                .hasStatusOk();
    }

    @ParameterizedTest
    @CsvSource({
        "UNAVAILABLE, 502, EMBEDDING_PROVIDER_UNAVAILABLE",
        "UNAUTHORIZED, 502, EMBEDDING_PROVIDER_UNAUTHORIZED",
        "QUOTA_EXCEEDED, 502, EMBEDDING_QUOTA_EXCEEDED",
        "INVALID_REQUEST, 502, EMBEDDING_REQUEST_REJECTED",
        "RATE_LIMITED, 429, EMBEDDING_RATE_LIMITED"
    })
    void providerFailuresBecomeApiErrors(Type type, int status, String code) throws IOException {
        String projectId = indexedProject();
        doThrow(new EmbeddingFailure(type, "provider-detail-must-not-leak"))
                .when(provider)
                .embed(any());

        MvcTestResult result = search(projectId, "{\"query\": \"alpha\"}");

        assertThat(result).hasStatus(status);
        assertThat((String) json(result).read("$.code")).isEqualTo(code);
        assertThat(body(result)).doesNotContain("provider-detail-must-not-leak");
        verify(provider).embed(List.of("alpha"));
    }

    @Test
    void queryNeverReachesTheLogs(CapturedOutput output) throws IOException {
        String projectId = indexedProject();

        assertThat(search(projectId, "{\"query\": \"secret-question-5d1e alpha\"}")).hasStatusOk();

        assertThat(output).doesNotContain("secret-question-5d1e").contains("Vector search on");
    }

    private String indexedProject() throws IOException {
        Path tree = tree();
        write(tree, "a.txt", "alpha");
        String projectId = project(tree);
        index(projectId);
        // Only the calls of the search are verified.
        reset(provider);
        return projectId;
    }

    private MvcTestResult search(String projectId, String body) {
        return mvc.post()
                .uri("/api/v1/projects/{id}/search", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static void assertError(MvcTestResult result, String code) {
        assertThat((String) json(result).read("$.code")).as(body(result)).isEqualTo(code);
    }

    private String index(String projectId) {
        MvcTestResult created =
                mvc.post().uri("/api/v1/projects/{id}/index-jobs", projectId).exchange();
        assertThat(created).hasStatus(HttpStatus.ACCEPTED);
        String jobId = json(created).read("$.id");
        String status =
                await().atMost(Duration.ofSeconds(20))
                        .until(
                                () ->
                                        json(mvc.get()
                                                        .uri("/api/v1/index-jobs/{id}", jobId)
                                                        .exchange())
                                                .read("$.status"),
                                TERMINAL::contains);
        assertThat(status).isEqualTo("COMPLETED");
        return json(created).read("$.snapshotId");
    }

    private Path tree() throws IOException {
        Path tree =
                Files.createDirectories(
                        TestFileSystemInitializer.ROOTS_DIR.resolve(
                                "work/search-" + UUID.randomUUID()));
        trees.add(tree);
        return tree;
    }

    private String project(Path tree) {
        MvcTestResult result =
                mvc.post()
                        .uri("/api/v1/workspaces/{id}/projects", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name": "P", "sourceType": "LOCAL_FOLDER", "rootKey": "work",
                                 "relativePath": "%s"}
                                """
                                        .formatted(tree.getFileName()))
                        .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return json(result).read("$.id");
    }

    private static void write(Path tree, String name, String content) throws IOException {
        Path file = tree.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static DocumentContext json(MvcTestResult result) {
        return JsonPath.parse(body(result));
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
