package com.codebasecopilot.shared.text;

import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.error.FieldViolation;
import java.text.Normalizer;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class DisplayNames {

    public static final int MAX_LENGTH = 100;

    private DisplayNames() {}

    /**
     * Strips and NFC-normalizes {@code raw}. Returns an empty string when {@code raw} is null or
     * blank, so each caller decides between a default and a required-field error.
     */
    public static String normalize(@Nullable String raw, String field) {
        if (raw == null) {
            return "";
        }
        String name = Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
        if (name.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid(field, "validation.name.control-chars");
        }
        if (name.codePointCount(0, name.length()) > MAX_LENGTH) {
            throw invalid(field, "validation.name.too-long");
        }
        return name;
    }

    public static ApiException invalid(String field, String messageKey) {
        return new ApiException(
                ErrorCode.VALIDATION_FAILED, List.of(new FieldViolation(field, messageKey)));
    }
}
