package com.codebasecopilot.shared.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND),
    WORKSPACE_NOT_EMPTY(HttpStatus.CONFLICT),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND),
    LOCAL_ROOTS_NOT_CONFIGURED(HttpStatus.CONFLICT),
    ROOT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PATH_OUTSIDE_ROOT(HttpStatus.BAD_REQUEST),
    PROJECT_ALREADY_EXISTS(HttpStatus.CONFLICT);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
