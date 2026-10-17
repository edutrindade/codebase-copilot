package com.codebasecopilot.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.IntegrationTest;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// Keeps the tokenizer aligned with the PostgreSQL "simple" parser (D-055).
@IntegrationTest
class IdentifierTokenizerParserIT {

    @Autowired private JdbcClient jdbcClient;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "public User getUserById(long user_id) { return repo.find(user_id); }",
                "import src/foo/Bar.java; // see a.b.C and my-file.ts",
                "Docs at https://example.com/api/v1?id=2&page=3#top, mail dev@example.com",
                "version 1.2.3, released 2024-01-15, pi=3.14, arch x86_64",
                "HTTPClient parseJSON2Xml IOError MAX_RETRY_COUNT kebab-case-name $scope",
                "foo::bar a->b \"quoted\" 'single' `tick` [list] {map} <T>",
                "calcularAção ação_calculada İstanbulCity ΣΟΦΙΑ straße 日本語Text",
                "List<String> f(Map<K, V> m, Foo<Bar<Baz>> z) <T extends Number> void g()",
                "if (a<b && c>d) return; <!-- html note --> <div class=\"box\">text</div><br/>"
            })
    void everyTermIsOneLexemeAndEveryQueryTermMatchesTheChunk(String content) {
        List<String> identifierTerms = IdentifierTokenizer.identifierTerms(content);
        List<String> queryTerms = IdentifierTokenizer.queryTerms(content);

        for (String term : identifierTerms) {
            assertThat(lexemes(term)).as("lexemes of %s", term).hasSize(1);
        }
        for (String term : queryTerms) {
            assertThat(lexemes(term)).as("lexemes of %s", term).hasSize(1);
        }

        List<String> indexed = lexemes(content + " " + String.join(" ", identifierTerms));
        assertThat(indexed)
                .containsAll(queryTerms.stream().flatMap(t -> lexemes(t).stream()).toList());
    }

    private List<String> lexemes(String text) {
        return jdbcClient
                .sql("SELECT unnest(tsvector_to_array(to_tsvector('simple', :text)))")
                .param("text", text)
                .query(String.class)
                .list();
    }
}
