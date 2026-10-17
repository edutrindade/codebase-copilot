package com.codebasecopilot.answering;

enum AnswerWarning {
    // Citations or markers that referred to an evidence or lines that were not sent.
    INVALID_CITATIONS_REMOVED,
    // The model answered, but nothing it cited holds.
    NO_VALID_CITATIONS
}
