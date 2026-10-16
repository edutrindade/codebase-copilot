package com.codebasecopilot.indexing;

// The job was retaken after its lease expired, or removed; this worker must stop.
class LostClaimException extends RuntimeException {

    LostClaimException() {
        super(null, null, false, false);
    }
}
