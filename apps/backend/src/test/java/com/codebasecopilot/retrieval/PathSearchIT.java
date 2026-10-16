package com.codebasecopilot.retrieval;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// The path channel (D-058), with the similar names measured in the F0 of Etapa 5.
@IntegrationTest
class PathSearchIT {

    private static final double THRESHOLD = PathSearch.DEFAULT_THRESHOLD;

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PathSearch search;

    private IndexedProjects projects;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        projects = new IndexedProjects(mvc, insertWorkspace(jdbc));
    }

    @AfterEach
    void removeTrees() throws IOException {
        projects.removeTrees();
    }

    @Test
    void fileNamedInTheQuestionBringsItsChunksInLineOrder() throws IOException {
        UUID snapshotId =
                projects.indexedSnapshot(
                        "retrieval/VectorSearch.java", lines(130),
                        "retrieval/VectorSearchPerformanceIT.java", "class Perf {}",
                        "retrieval/SearchService.java", "class SearchService {}");

        List<SearchHit> hits =
                search.search(snapshotId, "O que faz o VectorSearch.java?", THRESHOLD, 10);

        assertThat(hits).hasSizeGreaterThan(1);
        assertThat(hits)
                .extracting(hit -> hit.paths().getFirst())
                .containsOnly("retrieval/VectorSearch.java");
        assertThat(hits).extracting(SearchHit::startLine).isSorted().startsWith(1);
        assertThat(hits.getFirst().score()).isEqualTo(1.0);
    }

    @Test
    void pathWithDirectoriesRanksTheFileFirst() throws IOException {
        UUID snapshotId =
                projects.indexedSnapshot(
                        "docs/plans/etapa-4.md", "plano quatro",
                        "docs/plans/etapa-3.md", "plano três");

        List<SearchHit> hits =
                search.search(snapshotId, "resuma docs/plans/etapa-4.md", THRESHOLD, 10);

        // The shared directories put the neighbour above the threshold, below the file itself.
        assertThat(hits)
                .extracting(SearchHit::content)
                .containsExactly("plano quatro", "plano três");
        assertThat(hits.get(0).score()).isEqualTo(1.0);
        assertThat(hits.get(1).score()).isLessThan(1.0);
    }

    @Test
    void wordThatIsAFileNameOfTheSnapshotMatchesThatFileOnly() throws IOException {
        UUID snapshotId =
                projects.indexedSnapshot(
                        "indexing/IndexJob.java", "record IndexJob() {}",
                        "indexing/IndexJobApiIT.java", "class IndexJobApiIT {}",
                        "retrieval/SearchApiIT.java", "class SearchApiIT {}");

        assertThat(search.search(snapshotId, "como o IndexJob é criado?", THRESHOLD, 10))
                .extracting(hit -> hit.paths().getFirst())
                .containsExactly("indexing/IndexJob.java");
        // "search" is no file name of the snapshot, so it is not a path term.
        assertThat(search.search(snapshotId, "como funciona a search", THRESHOLD, 10)).isEmpty();
    }

    @Test
    void questionWithoutPathTermsFindsNothing() throws IOException {
        UUID snapshotId = projects.indexedSnapshot("pom.xml", "<project/>");

        assertThat(search.search(snapshotId, "como o reembolso é calculado?", THRESHOLD, 10))
                .isEmpty();
    }

    @Test
    void searchesOnlyTheRequestedSnapshot() throws IOException {
        UUID snapshotId = projects.indexedSnapshot("pom.xml", "<project>one</project>");
        projects.indexedSnapshot("pom.xml", "<project>two</project>");

        assertThat(search.search(snapshotId, "pom.xml", THRESHOLD, 10))
                .extracting(SearchHit::content)
                .containsExactly("<project>one</project>");
    }

    @Test
    void chunkSharedByTwoMatchedFilesIsOneResultWithEveryPath() throws IOException {
        UUID snapshotId =
                projects.indexedSnapshot(
                        "b/config.yml", "same: true",
                        "a/config.yml", "same: true");

        List<SearchHit> hits = search.search(snapshotId, "config.yml", THRESHOLD, 10);

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().paths()).containsExactly("a/config.yml", "b/config.yml");
    }

    @Test
    void thresholdBelowThePrefilterIsRejected() {
        assertThatThrownBy(() -> search.search(UUID.randomUUID(), "pom.xml", 0.4, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String lines(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> "int line" + i + " = " + i + ";")
                .collect(Collectors.joining("\n"));
    }
}
