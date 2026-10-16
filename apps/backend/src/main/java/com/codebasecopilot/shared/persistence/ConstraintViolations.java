package com.codebasecopilot.shared.persistence;

import org.springframework.dao.DataIntegrityViolationException;

public final class ConstraintViolations {

    private ConstraintViolations() {}

    // PostgreSQL names the violated constraint in the message; the driver is runtime-only.
    public static boolean violates(DataIntegrityViolationException ex, String constraint) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains("\"" + constraint + "\"");
    }
}
