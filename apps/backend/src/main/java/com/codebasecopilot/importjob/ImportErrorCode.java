package com.codebasecopilot.importjob;

// Stable codes stored in import_jobs.error_code; messages are import.error.<CODE> (pt-BR).
enum ImportErrorCode {
    ZIP_INVALID,
    ZIP_UNSAFE_PATH,
    ZIP_DUPLICATE_ENTRY,
    ZIP_TOO_MANY_ENTRIES,
    ZIP_ENTRY_TOO_LARGE,
    ZIP_UNCOMPRESSED_TOO_LARGE,
    ZIP_COMPRESSION_RATIO_EXCEEDED,
    ZIP_TOO_DEEP,
    IMPORT_INTERRUPTED,
    IMPORT_IO_ERROR
}
