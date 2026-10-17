package com.codebasecopilot.indexing.content;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

// High-precision formats only (P5 of Etapa 8, D-083): private key blocks and the published
// prefixes of known tokens. No entropy nor "password=" rules, because a false positive hides
// legitimate code. A miss is possible; the detection is not a guarantee (07, section 5).
public final class SecretDetector {

    private static final String BEFORE = "(?<![A-Za-z0-9_])";
    private static final String AFTER = "(?![A-Za-z0-9])";

    private static final List<Pattern> PATTERNS =
            List.of(
                    // A BEGIN line followed by key material, so code that only names the
                    // delimiter (a PEM parser) is not matched. "\n" may be an escape in a string.
                    Pattern.compile(
                            "-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY(?: BLOCK)?-----"
                                    + "(?:\\s|\\\\n)*(?:[A-Za-z-]+: [^\\r\\n]*(?:\\s|\\\\n)+)*"
                                    + "[A-Za-z0-9+/=]{20}"),
                    Pattern.compile(BEFORE + "(?:AKIA|ASIA)[A-Z0-9]{16}" + AFTER),
                    Pattern.compile(BEFORE + "gh[pousr]_[A-Za-z0-9]{36,}"),
                    Pattern.compile(BEFORE + "github_pat_[A-Za-z0-9_]{22,}"),
                    Pattern.compile(BEFORE + "(?:sk|rk)_(?:live|test)_[A-Za-z0-9]{20,}"),
                    Pattern.compile(BEFORE + "sk_org_[A-Za-z0-9]{20,}"),
                    Pattern.compile(BEFORE + "(?:xox[bp]|xapp|xoxe\\.xapp|xwfp)-[A-Za-z0-9-]{20,}"),
                    // Not all OpenAI prefixes are published: "sk-" with a long body and a digit.
                    Pattern.compile(
                            "(?<![A-Za-z0-9_-])sk-(?=[A-Za-z0-9_-]*[0-9])[A-Za-z0-9_-]{32,}"));

    private SecretDetector() {}

    // Every pattern is ASCII, so the bytes are read as Latin-1: no decoding can fail and a
    // UTF-8 file is matched as if it were decoded.
    public static boolean containsPossibleSecret(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
