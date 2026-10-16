package com.codebasecopilot.indexing.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// SHA-256 in lowercase hex (D-042).
public final class ContentHash {

    private ContentHash() {}

    public static String of(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    public static String of(String text) {
        return of(text.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
