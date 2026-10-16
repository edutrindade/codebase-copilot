package com.codebasecopilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codebasecopilot.providers.ProviderProperties;
import com.codebasecopilot.shared.fs.Directories;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Manual smoke test against the real provider (D-047), outside ./mvnw verify. It sends the
// sample repository below and one query to the provider; the key comes from EMBEDDING_API_KEY
// (.env or environment):
// ./mvnw verify -Dtest.excluded-groups= -Dgroups=smoke -Dtest=none
//     -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=EmbeddingSmokeIT
@Tag("smoke")
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
            "app.embeddings.provider=openai"
        })
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestFileSystemInitializer.class)
@Import(TestcontainersConfiguration.class)
class EmbeddingSmokeIT {

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED");

    // No file shares words with the query: only a semantic match finds the refund service.
    private static final String QUERY = "How does a customer get their money back?";
    private static final String EXPECTED_PATH = "src/billing/RefundService.java";

    private static final Map<String, String> SAMPLE_REPOSITORY =
            Map.of(
                    EXPECTED_PATH,
                    """
                    package billing;

                    public class RefundService {

                        private final PaymentGateway gateway;

                        public RefundService(PaymentGateway gateway) {
                            this.gateway = gateway;
                        }

                        // Reverses the charge of a cancelled order to the original card.
                        public void refund(Order order) {
                            if (!order.isCancelled()) {
                                throw new IllegalStateException("order not cancelled");
                            }
                            gateway.reverseCharge(order.chargeId(), order.total());
                        }
                    }
                    """,
                    "src/auth/PasswordHasher.java",
                    """
                    package auth;

                    import org.springframework.security.crypto.bcrypt.BCrypt;

                    public final class PasswordHasher {

                        public String hash(String password) {
                            return BCrypt.hashpw(password, BCrypt.gensalt(12));
                        }

                        public boolean matches(String password, String hashed) {
                            return BCrypt.checkpw(password, hashed);
                        }
                    }
                    """,
                    "src/storage/ImageResizer.java",
                    """
                    package storage;

                    import java.awt.image.BufferedImage;

                    public final class ImageResizer {

                        public BufferedImage thumbnail(BufferedImage source, int width) {
                            int height = source.getHeight() * width / source.getWidth();
                            BufferedImage target =
                                    new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                            target.getGraphics().drawImage(source, 0, 0, width, height, null);
                            return target;
                        }
                    }
                    """,
                    "README.md",
                    """
                    # Shop

                    Sample online shop used by the embedding smoke test.
                    """);

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private ProviderProperties properties;

    private Path tree;

    @AfterEach
    void removeTree() throws IOException {
        if (tree != null) {
            Directories.deleteRecursively(tree);
        }
    }

    @Test
    void indexesAndSearchesWithTheRealProvider() throws IOException {
        tree = sampleRepository();
        String projectId = project();

        DocumentContext job = runJob(projectId);
        assertThat((String) job.read("$.status"))
                .as("job %s", job.jsonString())
                .isEqualTo("COMPLETED");
        String snapshotId = job.read("$.snapshotId");
        DocumentContext snapshot =
                json(mvc.get().uri("/api/v1/snapshots/{id}", snapshotId).exchange());
        int chunks = snapshot.read("$.counts.chunks");
        Number tokens = snapshot.read("$.embedding.tokens");
        assertThat((String) snapshot.read("$.embedding.provider")).isEqualTo("openai");
        assertThat((String) snapshot.read("$.embedding.model")).isEqualTo(properties.model());
        assertThat((Integer) snapshot.read("$.embedding.dimensions"))
                .isEqualTo(properties.dimensions());
        assertThat(tokens).isNotNull();
        assertThat(tokens.longValue()).isPositive();
        assertThat(chunksWithoutVector(snapshotId)).isZero();

        long start = System.nanoTime();
        MvcTestResult search =
                mvc.post()
                        .uri("/api/v1/projects/{id}/search", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"%s\", \"limit\": 3}".formatted(QUERY))
                        .exchange();
        long searchMillis = (System.nanoTime() - start) / 1_000_000;
        assertThat(search).hasStatusOk();
        DocumentContext result = json(search);
        assertThat((String) result.read("$.results[0].path")).isEqualTo(EXPECTED_PATH);

        Duration indexing =
                Duration.between(
                        Instant.parse(job.read("$.startedAt")),
                        Instant.parse(job.read("$.completedAt")));
        System.out.printf(
                Locale.ROOT,
                "Embedding smoke test %s: %s/%s (%d dimensions), %d chunks, %d tokens,"
                        + " index job %d ms, search %d ms, top score %.4f%n",
                LocalDate.now(),
                properties.provider().name().toLowerCase(Locale.ROOT),
                properties.model(),
                properties.dimensions(),
                chunks,
                tokens.longValue(),
                indexing.toMillis(),
                searchMillis,
                ((Number) result.read("$.results[0].score")).doubleValue());
    }

    private Path sampleRepository() throws IOException {
        Path root =
                Files.createDirectories(
                        TestFileSystemInitializer.ROOTS_DIR.resolve(
                                "work/smoke-" + UUID.randomUUID()));
        for (Map.Entry<String, String> file : SAMPLE_REPOSITORY.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
        return root;
    }

    private String project() {
        MvcTestResult workspace =
                mvc.post()
                        .uri("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Smoke\"}")
                        .exchange();
        assertThat(workspace).hasStatus(HttpStatus.CREATED);
        MvcTestResult project =
                mvc.post()
                        .uri(
                                "/api/v1/workspaces/{id}/projects",
                                (String) json(workspace).read("$.id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name": "Shop", "sourceType": "LOCAL_FOLDER", "rootKey": "work",
                                 "relativePath": "%s"}
                                """
                                        .formatted(tree.getFileName()))
                        .exchange();
        assertThat(project).hasStatus(HttpStatus.CREATED);
        return json(project).read("$.id");
    }

    private DocumentContext runJob(String projectId) {
        MvcTestResult created =
                mvc.post().uri("/api/v1/projects/{id}/index-jobs", projectId).exchange();
        assertThat(created).hasStatus(HttpStatus.ACCEPTED);
        String jobId = json(created).read("$.id");
        await().atMost(Duration.ofMinutes(2))
                .until(() -> job(jobId).read("$.status"), TERMINAL::contains);
        return job(jobId);
    }

    private DocumentContext job(String jobId) {
        return json(mvc.get().uri("/api/v1/index-jobs/{id}", jobId).exchange());
    }

    private int chunksWithoutVector(String snapshotId) {
        return jdbc.sql(
                        """
                        SELECT count(*)
                        FROM snapshots s
                        JOIN snapshot_files f ON f.snapshot_id = s.id
                        JOIN chunks c ON c.project_id = f.project_id
                                     AND c.content_hash = f.content_hash
                                     AND c.chunking_version = s.chunking_version
                        WHERE s.id = :snapshotId
                          AND NOT EXISTS (
                              SELECT 1 FROM chunk_embeddings e
                              WHERE e.chunk_hash = c.chunk_hash
                                AND e.provider = s.embedding_provider
                                AND e.model = s.embedding_model
                                AND e.dimensions = s.embedding_dimensions)
                        """)
                .param("snapshotId", UUID.fromString(snapshotId))
                .query(Integer.class)
                .single();
    }

    private static DocumentContext json(MvcTestResult result) {
        try {
            return JsonPath.parse(result.getResponse().getContentAsString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
