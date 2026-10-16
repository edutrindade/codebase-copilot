package com.codebasecopilot.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IdentifierTokenizerTest {

    static Stream<Arguments> identifiers() {
        return Stream.of(
                Arguments.of("getUserById", List.of("getUserById", "get", "User", "By", "Id")),
                Arguments.of("UserService", List.of("UserService", "User", "Service")),
                Arguments.of("user_id", List.of("user", "id")),
                Arguments.of("MAX_RETRY_COUNT", List.of("MAX", "RETRY", "COUNT")),
                Arguments.of("get_userById", List.of("get", "userById", "user", "By", "Id")),
                Arguments.of("kebab-case-name", List.of("kebab", "case", "name")),
                Arguments.of("HTTPClient", List.of("HTTPClient", "HTTP", "Client")),
                Arguments.of("parseJSON", List.of("parseJSON", "parse", "JSON")),
                Arguments.of("IOError", List.of("IOError", "IO", "Error")),
                Arguments.of(
                        "parseJSON2Xml", List.of("parseJSON2Xml", "parse", "JSON", "2", "Xml")),
                Arguments.of("v2", List.of("v2", "v", "2")),
                Arguments.of("$http.get", List.of("http", "get")),
                Arguments.of("calcularAção", List.of("calcularAção", "calcular", "Ação")),
                Arguments.of("ação_calculada", List.of("ação", "calculada")),
                Arguments.of("İstanbulCity", List.of("İstanbulCity", "İstanbul", "City")),
                Arguments.of("caféBar", List.of("caféBar", "café", "Bar")));
    }

    @ParameterizedTest
    @MethodSource("identifiers")
    void splitsCompoundIdentifiers(String text, List<String> terms) {
        assertThat(IdentifierTokenizer.identifierTerms(text)).containsExactlyElementsOf(terms);
    }

    static Stream<Arguments> gluedWords() {
        return Stream.of(
                Arguments.of("src/foo/Bar.java", List.of("src", "foo", "Bar", "java")),
                Arguments.of("repo.find(id);", List.of("repo", "find", "id")),
                Arguments.of("my-file.ts", List.of("my", "file", "ts")),
                Arguments.of("dev@example.com", List.of("dev", "example", "com")),
                Arguments.of("1.2.3", List.of("1", "2", "3")),
                Arguments.of(
                        "https://example.com/api/v1?id=2",
                        List.of("https", "example", "com", "api", "v1", "v", "1", "id", "2")),
                Arguments.of("foo::bar a->b", List.of("foo", "bar", "a", "b")));
    }

    @ParameterizedTest
    @MethodSource("gluedWords")
    void keepsWordsGluedWithoutWhitespace(String text, List<String> terms) {
        assertThat(IdentifierTokenizer.identifierTerms(text)).containsExactlyElementsOf(terms);
    }

    static Stream<Arguments> angleBrackets() {
        return Stream.of(
                Arguments.of("<T> void run", List.of("T")),
                Arguments.of("f <T extends Number> x", List.of("T", "extends", "Number")),
                Arguments.of("List<String> items", List.of("List", "String")),
                Arguments.of("if (a<b && c>d) go", List.of("a", "b", "c", "d")),
                Arguments.of("<!-- note --> text", List.of("note")),
                Arguments.of("<div class=x>", List.of("div", "class", "x")));
    }

    @ParameterizedTest
    @MethodSource("angleBrackets")
    void keepsWordsBetweenAngleBrackets(String text, List<String> terms) {
        assertThat(IdentifierTokenizer.identifierTerms(text)).containsExactlyElementsOf(terms);
    }

    @Test
    void leavesLooseSinglePartWordsToTheContent() {
        assertThat(IdentifierTokenizer.identifierTerms("return the value; // done.")).isEmpty();
        assertThat(IdentifierTokenizer.identifierTerms("$scope \"quoted\" (x)")).isEmpty();
        assertThat(IdentifierTokenizer.identifierTerms("")).isEmpty();
    }

    @Test
    void separatesWordsOnAnyWhitespace() {
        assertThat(IdentifierTokenizer.identifierTerms("a.b c.d\te.f\ng"))
                .containsExactly("a", "b", "c", "d", "e", "f");
    }

    @Test
    void keepsTheFirstSpellingOfRepeatedTerms() {
        assertThat(IdentifierTokenizer.identifierTerms("getUser(user) GETUser get_user"))
                .containsExactly("getUser", "get", "User");
    }

    @Test
    void queryTermsIncludeEveryWordWithoutStopwords() {
        assertThat(IdentifierTokenizer.queryTerms("Como o reembolso é calculado?"))
                .containsExactly("reembolso", "calculado");
        assertThat(IdentifierTokenizer.queryTerms("Where is getUserById defined?"))
                .containsExactly("getUserById", "get", "User", "Id", "defined");
        assertThat(IdentifierTokenizer.queryTerms("O que faz src/foo/Bar.java?"))
                .containsExactly("faz", "src", "foo", "Bar", "java");
    }

    @Test
    void queryWithOnlyStopwordsHasNoTerms() {
        assertThat(IdentifierTokenizer.queryTerms("What is the? O que é isso?")).isEmpty();
        assertThat(IdentifierTokenizer.queryTerms("?! ...")).isEmpty();
    }

    @Test
    void sameTextGivesTheSameTerms() {
        String text = "class UserService { User getUserById(long user_id) { return null; } }";

        assertThat(IdentifierTokenizer.identifierTerms(text))
                .isEqualTo(IdentifierTokenizer.identifierTerms(text));
        assertThat(IdentifierTokenizer.queryTerms(text))
                .isEqualTo(IdentifierTokenizer.queryTerms(text));
    }

    @Test
    void hasItsOwnVersion() {
        assertThat(IdentifierTokenizer.VERSION).isEqualTo("ident-v1");
    }
}
