package com.codebasecopilot.shared.fs;

import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

// Purely syntactic: never touches the file system (D-020, D-032).
public final class RelativePaths {

    private RelativePaths() {}

    /** NUL, backslash and control characters are a validation failure (precedence step 1). */
    public static boolean hasForbiddenCharacters(String raw) {
        return raw.chars().anyMatch(c -> c == '\\' || Character.isISOControl(c));
    }

    /**
     * Returns the NFC-normalized path with {@code /} separators, without empty or {@code .}
     * segments; the root itself is {@code ""}.
     *
     * @throws ApiException {@code PATH_OUTSIDE_ROOT} for an absolute path or a {@code ..} segment
     */
    public static String normalize(String raw) {
        String path = Normalizer.normalize(raw, Normalizer.Form.NFC);
        if (path.startsWith("/")) {
            throw new ApiException(ErrorCode.PATH_OUTSIDE_ROOT);
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                throw new ApiException(ErrorCode.PATH_OUTSIDE_ROOT);
            }
            if (!segment.isEmpty() && !segment.equals(".")) {
                segments.add(segment);
            }
        }
        return String.join("/", segments);
    }
}
