package com.codebasecopilot.indexing;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.indexing.content.ContentHash;
import com.codebasecopilot.indexing.content.LineChunker;
import com.codebasecopilot.indexing.content.TextClassifier;
import com.codebasecopilot.retrieval.IndexedProjects;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// Files with a possible secret in the content are UNSUPPORTED SENSITIVE_CONTENT, with nothing
// stored but the row (P5 of Etapa 8).
@IntegrationTest
class SensitiveContentIT {

    private static final String TOKEN = "ghp_" + "EVALSYNTHETICnotarealtoken0000000000";
    private static final String CONFIG = "aws:\n  access-key-id: " + "AKIA" + "EVALSYNTHETIC123\n";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private IndexStore store;

    private IndexedProjects projects;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        projects = new IndexedProjects(mvc, insertWorkspace(jdbc));
    }

    @AfterEach
    void tearDown() throws IOException {
        projects.removeTrees();
    }

    @Test
    void filesWithAPossibleSecretAreListedWithoutTheirContent() throws IOException {
        UUID snapshotId =
                projects.indexedSnapshot(
                        "src/Main.java",
                        "class Main {}\n",
                        "scripts/publish.sh",
                        "#!/bin/sh\nGITHUB_TOKEN=\"" + TOKEN + "\"\n",
                        "src/main/resources/application.yml",
                        CONFIG);

        assertThat(files(snapshotId))
                .containsExactly(
                        "scripts/publish.sh:UNSUPPORTED:SENSITIVE_CONTENT",
                        "src/Main.java:INDEXED:null",
                        "src/main/resources/application.yml:UNSUPPORTED:SENSITIVE_CONTENT");
        assertThat(storedText()).noneMatch(text -> text.toUpperCase().contains("EVALSYNTHETIC"));
        assertThat(
                        mvc.get()
                                .uri("/api/v1/snapshots/{id}/files?status=UNSUPPORTED", snapshotId)
                                .exchange())
                .hasStatusOk()
                .bodyText()
                .doesNotContain("EVALSYNTHETIC")
                .doesNotContain("GITHUB_TOKEN")
                .contains("\"chunkCount\":0");
    }

    // A content chunked before the detection existed is not reused by the next job.
    @Test
    void contentChunkedBeforeTheDetectionIsExcluded() throws IOException {
        Path tree = projects.tree();
        IndexedProjects.write(tree, "src/Main.java", "class Main {}\n");
        IndexedProjects.write(tree, "src/main/resources/application.yml", CONFIG);
        UUID projectId = projects.project(tree);
        byte[] bytes = CONFIG.getBytes(StandardCharsets.UTF_8);
        TextClassifier.Text text = (TextClassifier.Text) TextClassifier.classify(bytes);
        store.insertContents(
                projectId,
                List.of(
                        new IndexStore.NewContent(
                                ContentHash.of(bytes),
                                text.content(),
                                bytes.length,
                                text.lines().size(),
                                LineChunker.chunk(text.lines()))),
                Instant.now());

        UUID snapshotId = projects.index(projectId);

        assertThat(files(snapshotId))
                .contains("src/main/resources/application.yml:UNSUPPORTED:SENSITIVE_CONTENT");
    }

    private List<String> files(UUID snapshotId) {
        return jdbc.sql(
                        """
                        SELECT relative_path || ':' || status || ':' || coalesce(reason, 'null')
                        FROM snapshot_files WHERE snapshot_id = :id ORDER BY relative_path
                        """)
                .param("id", snapshotId)
                .query(String.class)
                .list();
    }

    private List<String> storedText() {
        return jdbc.sql(
                        """
                        SELECT content FROM file_contents
                        UNION ALL SELECT content FROM chunks
                        UNION ALL SELECT identifier_terms || ' ' || tsv::text FROM chunk_lexical
                        """)
                .query(String.class)
                .list();
    }
}
