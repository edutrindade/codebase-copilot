package com.codebasecopilot.answering;

// Messages are answer.warning.<CODE> (pt-BR).
enum AnswerWarning {
    // Retrieval found nothing that fits the context; the model was not called.
    NO_EVIDENCE,
    // Citations or markers that referred to an evidence or lines that were not sent.
    INVALID_CITATIONS_REMOVED,
    // The model answered, but nothing it cited holds.
    NO_VALID_CITATIONS
}
