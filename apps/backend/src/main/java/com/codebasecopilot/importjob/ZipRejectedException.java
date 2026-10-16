package com.codebasecopilot.importjob;

// The archive breaks a rule; the job ends as REJECTED.
class ZipRejectedException extends Exception {

    private final ImportErrorCode code;

    ZipRejectedException(ImportErrorCode code) {
        super(code.name(), null, false, false);
        this.code = code;
    }

    ImportErrorCode code() {
        return code;
    }
}
