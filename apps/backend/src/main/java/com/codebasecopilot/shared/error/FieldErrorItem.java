package com.codebasecopilot.shared.error;

/** Item of the {@code errors} property of a validation problem: only field and message. */
public record FieldErrorItem(String field, String message) {}
