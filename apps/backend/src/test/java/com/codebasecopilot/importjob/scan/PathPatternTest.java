package com.codebasecopilot.importjob.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PathPatternTest {

    @ParameterizedTest
    @CsvSource({
        // pattern, path, directory, matches
        "*.log,       app.log,           false, true",
        "*.log,       a/b/app.log,       false, true",
        "*.log,       app.log.txt,       false, false",
        "docs/*.md,   docs/a.md,         false, true",
        "docs/*.md,   x/docs/a.md,       false, false",
        "docs/*.md,   docs/sub/a.md,     false, false",
        "/docs/*.md,  docs/a.md,         false, true",
        "**/gen/**,   gen,               true,  true",
        "**/gen/**,   a/b/gen,           true,  true",
        "**/gen/**,   a/gen/x.java,      false, true",
        "**/gen/**,   a/general,         true,  false",
        "build/,      build,             true,  true",
        "build/,      a/build,           true,  true",
        "build/,      build,             false, false",
        "a?c,         abc,               false, true",
        "a?c,         a/c,               false, false",
        "a?c,         abbc,              false, false",
        "Makefile,    makefile,          false, false",
        "Makefile,    src/Makefile,      false, true",
        "src/**/*.ts, src/a/b/c.ts,      false, true",
        "src/**/*.ts, src/c.ts,          false, true",
        "a.b,         axb,               false, false"
    })
    void matches(String pattern, String path, boolean directory, boolean expected) {
        assertThat(PathPattern.compile(pattern).matches(path, directory)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"", "/", "!x", "[a]", "a]", "a\\b", "../x", "a/../b", "./a", "a//b", "a\tb"})
    void rejectsInvalidPatterns(String pattern) {
        assertThatThrownBy(() -> PathPattern.compile(pattern))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201})
    void limitsLength(int length) {
        String pattern = "a".repeat(length);
        if (length <= PathPattern.MAX_LENGTH) {
            assertThat(PathPattern.compile(pattern).source()).isEqualTo(pattern);
        } else {
            assertThatThrownBy(() -> PathPattern.compile(pattern))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
