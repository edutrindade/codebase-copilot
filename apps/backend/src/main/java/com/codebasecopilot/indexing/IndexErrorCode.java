package com.codebasecopilot.indexing;

// Stable codes stored in index_jobs.error_code; messages are index.error.<CODE> (pt-BR).
enum IndexErrorCode {
    SOURCE_ROOT_NOT_CONFIGURED,
    SOURCE_DIRECTORY_NOT_FOUND,
    SOURCE_IS_SYMLINK,
    SOURCE_NOT_READABLE,
    SOURCE_NOT_IMPORTED,
    SCAN_LIMIT_REACHED,
    PROJECT_SIZE_LIMIT_EXCEEDED,
    NO_INCLUDED_FILES,
    INDEX_IO_ERROR,
    INDEX_ATTEMPTS_EXHAUSTED
}
