package com.codebasecopilot.shared.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RelativePathsTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            emptyValue = "",
            value = {
                "codebase-copilot      | codebase-copilot",
                "a//b/                 | a/b",
                "./a/./b               | a/b",
                "a/b/.                 | a/b",
                "''                    | ''",
                ".                     | ''",
                "./                    | ''",
                ".//                   | ''",
                "Foo/bar               | Foo/bar",
                "...                   | ...",
                "a/..b/c..             | a/..b/c..",
                "'my repo/ src '       | 'my repo/ src '"
            })
    void normalizesSyntactically(String raw, String expected) {
        assertThat(RelativePaths.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/abs", "/", "//", "..", "a/..", "a/../b", "../x", "./../x"})
    void rejectsPathsOutsideRoot(String raw) {
        assertThatThrownBy(() -> RelativePaths.normalize(raw))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PATH_OUTSIDE_ROOT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\\b", "a\u0000b", "a\u0007b", "a\nb", "a\u007Fb", "..\\x"})
    void detectsForbiddenCharacters(String raw) {
        assertThat(RelativePaths.hasForbiddenCharacters(raw)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a/b", "../x", "/abs", "pasta com espaço/ção"})
    void allowsOrdinaryCharacters(String raw) {
        assertThat(RelativePaths.hasForbiddenCharacters(raw)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"Projéto/a, Projéto/a", "Projéto/a, Projéto/a"})
    void normalizesToNfcSoEquivalentFormsMatch(String raw, String expected) {
        assertThat(RelativePaths.normalize(raw)).isEqualTo(expected);
    }
}
