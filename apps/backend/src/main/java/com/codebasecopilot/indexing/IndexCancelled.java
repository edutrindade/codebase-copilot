package com.codebasecopilot.indexing;

// A cancellation was requested for the running job; the worker stops and ends it as CANCELLED.
class IndexCancelled extends RuntimeException {

    IndexCancelled() {
        super(null, null, false, false);
    }
}
