package com.codebasecopilot.shared.error;

import java.util.List;

/** Domain error translated to a {@code ProblemDetail} by {@link GlobalExceptionHandler}. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldViolation> violations;

    public ApiException(ErrorCode code) {
        this(code, List.of());
    }

    public ApiException(ErrorCode code, List<FieldViolation> violations) {
        super(code.name());
        this.code = code;
        this.violations = List.copyOf(violations);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
