package com.codebasecopilot.retrieval;

import static com.codebasecopilot.ImportJobRows.insertWorkspace;
import static com.codebasecopilot.retrieval.IndexedProjects.write;
import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// The lexical channel over snapshots built by the real pipeline (D-056, D-057).
@IntegrationTest
class LexicalSearchIT {

    private static final String SERVICE =
            "class UserService {\n  User getUserById(long id) {\n    return repo.find(id);\n  }\n}";

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private LexicalSearch search;

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

    @ParameterizedTest
    @ValueSource(
            strings = {
                "getUserById",
                "user by id",
                "get user",
                "Onde fica o GETUSERBYID?",
                "como buscar um usuário pelo id do user"
            })
    void compoundIdentifierIsFoundByItsFormAndByItsParts(String question) throws IOException {
        UUID snapshotId =
                projects.indexedSnapshot(
                        "src/UserService.java",
                        SERVICE,
                        "docs/notes.md",
                        "Notes about the weather and the garden.");

        List<SearchHit> hits = search.search(snapshotId, question, 10);

        assertThat(hits)
                .extracting(hit -> hit.paths().getFirst())
                .first()
                .isEqualTo("src/UserService.java");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "getUserById & | ! : ' ( ) <-> * \\",
                "getUserById'); DROP TABLE chunks; --",
                "getUserById:*",
                "'getUserById'",
                "(getUserById | !getUserById)"
            })
    void tsqueryOperatorsInTheQuestionDoNotBreakTheQuery(String question) throws IOException {
        UUID snapshotId = projects.indexedSnapshot("src/UserService.java", SERVICE);

        assertThat(search.search(snapshotId, question, 10))
                .extracting(hit -> hit.paths().getFirst())
                .containsExactly("src/UserService.java");
    }

    @ParameterizedTest
    @ValueSource(strings = {"&& || !!", "o que é isso?", "what is the"})
    void questionWithoutTermsFindsNothing(String question) throws IOException {
        UUID snapshotId = projects.indexedSnapshot("a.txt", "o que é isso what is the");

        assertThat(search.search(snapshotId, question, 10)).isEmpty();
    }

    @Test
    void searchesOnlyTheRequestedSnapshot() throws IOException {
        Path tree = projects.tree();
        write(tree, "billing.txt", "invoice payment");
        UUID projectId = projects.project(tree);
        UUID first = projects.index(projectId);
        write(tree, "billing.txt", "refund ledger");
        UUID second = projects.index(projectId);
        projects.indexedSnapshot("billing.txt", "invoice payment refund ledger");

        assertThat(search.search(first, "invoice refund", 10))
                .extracting(SearchHit::content)
                .containsExactly("invoice payment");
        assertThat(search.search(second, "invoice refund", 10))
                .extracting(SearchHit::content)
                .containsExactly("refund ledger");
    }

    @Test
    void chunkSharedByTwoFilesIsOneResultWithEveryPath() throws IOException {
        UUID snapshotId =
                projects.indexedSnapshot(
                        "b/copy.txt", "shared ledger text",
                        "a/original.txt", "shared ledger text",
                        "c/other.txt", "unrelated words");

        List<SearchHit> hits = search.search(snapshotId, "ledger", 10);

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().paths()).containsExactly("a/original.txt", "b/copy.txt");
    }

    @Test
    void equalScoresAreOrderedByPathAndRepeatTheSameOrder() throws IOException {
        UUID snapshotId =
                projects.indexedSnapshot(
                        "c-login.txt", "user login session",
                        "b-pay.txt", "charge payment",
                        "a-pay.txt", "payment charge");

        List<SearchHit> hits = search.search(snapshotId, "payment charge login", 10);

        assertThat(hits)
                .extracting(hit -> hit.paths().getFirst())
                .containsExactly("a-pay.txt", "b-pay.txt", "c-login.txt");
        assertThat(hits.get(0).score()).isEqualTo(hits.get(1).score());
        assertThat(search.search(snapshotId, "payment charge login", 10)).isEqualTo(hits);
    }

    @Test
    void identifierTermsWeighMoreThanProse() throws IOException {
        UUID snapshotId =
                projects.indexedSnapshot(
                        "prose.md", "the order total is computed later",
                        "Order.java", "int orderTotal = 0;");

        assertThat(search.search(snapshotId, "order total", 10))
                .extracting(hit -> hit.paths().getFirst())
                .containsExactly("Order.java", "prose.md");
    }

    @Test
    void limitBoundsTheResults() throws IOException {
        UUID snapshotId =
                projects.indexedSnapshot(
                        "a.txt", "ledger one", "b.txt", "ledger two", "c.txt", "ledger three");

        assertThat(search.search(snapshotId, "ledger", 2)).hasSize(2);
    }
}
