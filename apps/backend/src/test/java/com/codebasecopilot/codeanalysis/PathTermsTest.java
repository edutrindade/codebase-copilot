package com.codebasecopilot.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PathTermsTest {

    @Test
    void pathsAndFileNamesArePathTerms() {
        PathTerms.Terms terms =
                PathTerms.of("O que faz src/main/App.java e o pom.xml? Veja `etapa-4.md`.");

        assertThat(terms.paths()).containsExactly("src/main/App.java", "pom.xml", "etapa-4.md");
    }

    @Test
    void leadingDotSlashAndSlashAreDropped() {
        assertThat(PathTerms.of("./apps/backend e /docs/README.md").paths())
                .containsExactly("apps/backend", "docs/README.md");
    }

    @Test
    void versionsAndAbbreviationsAreNotFileNames() {
        assertThat(PathTerms.of("versão 1.2.3 do click").paths()).isEmpty();
    }

    @Test
    void otherWordsAreNameCandidatesWithoutStopwordsOrShortWords() {
        PathTerms.Terms terms = PathTerms.of("Como o IndexJob usa o heartbeat_at? E as id?");

        assertThat(terms.names()).containsExactly("IndexJob", "usa", "heartbeat_at");
    }

    @Test
    void repeatedTermsAppearOnceIgnoringCase() {
        PathTerms.Terms terms = PathTerms.of("pom.xml POM.xml IndexJob indexjob");

        assertThat(terms.paths()).containsExactly("pom.xml");
        assertThat(terms.names()).containsExactly("IndexJob");
    }

    @Test
    void punctuationOnlyHasNoTerms() {
        PathTerms.Terms terms = PathTerms.of("?? -- // ...");

        assertThat(terms.paths()).isEmpty();
        assertThat(terms.names()).isEmpty();
    }
}
