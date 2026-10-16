package com.codebasecopilot.shared.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.error.FieldViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DisplayNamesTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n", " "})
    void blankBecomesEmpty(String raw) {
        assertThat(DisplayNames.normalize(raw, "name")).isEmpty();
    }

    @Test
    void stripsSurroundingWhitespace() {
        assertThat(DisplayNames.normalize("  Meus  repos \t", "name")).isEqualTo("Meus  repos");
    }

    @Test
    void normalizesToNfc() {
        assertThat(DisplayNames.normalize("Proje\u0301to", "name")).isEqualTo("Proj\u00e9to");
    }

    @Test
    void acceptsMaxLengthCountedInCodePoints() {
        String emojis = "😀".repeat(DisplayNames.MAX_LENGTH);

        assertThat(DisplayNames.normalize(emojis, "name")).isEqualTo(emojis);
    }

    @Test
    void countsLengthAfterNormalization() {
        String decomposed = "e\u0301".repeat(DisplayNames.MAX_LENGTH);

        assertThat(DisplayNames.normalize(decomposed, "name")).hasSize(DisplayNames.MAX_LENGTH);
    }

    @Test
    void rejectsTooLong() {
        assertRejected("a".repeat(DisplayNames.MAX_LENGTH + 1), "validation.name.too-long");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\u0000b", "a\u0007b", "a\nb", "a\u007Fb", "a\u0085b"})
    void rejectsInnerControlCharacters(String raw) {
        assertRejected(raw, "validation.name.control-chars");
    }

    private static void assertRejected(String raw, String messageKey) {
        ApiException ex =
                catchThrowableOfType(ApiException.class, () -> DisplayNames.normalize(raw, "name"));

        assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ex.violations()).containsExactly(new FieldViolation("name", messageKey));
    }
}
