package com.codebasecopilot.indexing;

// Ends the job as FAILED with a stable code (D-044).
class IndexFailure extends RuntimeException {

    private final IndexErrorCode code;

    IndexFailure(IndexErrorCode code) {
        super(code.name(), null, false, false);
        this.code = code;
    }

    IndexErrorCode code() {
        return code;
    }
}
