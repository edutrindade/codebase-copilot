package com.codebasecopilot.answering;

import com.codebasecopilot.providers.GenerationFailure;
import com.codebasecopilot.shared.error.ErrorCode;
import org.jspecify.annotations.Nullable;

// Codes of a FAILED answer, stored in history and returned in error.code; messages are
// answer.error.<CODE> (pt-BR).
enum AnswerErrorCode {
    GENERATION_PROVIDER_UNAUTHORIZED,
    GENERATION_PROVIDER_UNAVAILABLE,
    GENERATION_RATE_LIMITED,
    GENERATION_QUOTA_EXCEEDED,
    GENERATION_REQUEST_REJECTED,
    GENERATION_OUTPUT_TRUNCATED,
    GENERATION_REFUSED,
    GENERATION_OUTPUT_INVALID,
    EMBEDDING_PROVIDER_UNAUTHORIZED,
    EMBEDDING_PROVIDER_UNAVAILABLE,
    EMBEDDING_RATE_LIMITED,
    EMBEDDING_QUOTA_EXCEEDED,
    EMBEDDING_REQUEST_REJECTED,
    ANSWER_INTERRUPTED;

    static AnswerErrorCode of(GenerationFailure.Type type) {
        return switch (type) {
            case UNAUTHORIZED -> GENERATION_PROVIDER_UNAUTHORIZED;
            case UNAVAILABLE -> GENERATION_PROVIDER_UNAVAILABLE;
            case RATE_LIMITED -> GENERATION_RATE_LIMITED;
            case QUOTA_EXCEEDED -> GENERATION_QUOTA_EXCEEDED;
            case INVALID_REQUEST -> GENERATION_REQUEST_REJECTED;
            case OUTPUT_TRUNCATED -> GENERATION_OUTPUT_TRUNCATED;
            case REFUSED -> GENERATION_REFUSED;
        };
    }

    // Null for a code that is not a failure of the question embedding.
    static @Nullable AnswerErrorCode ofEmbedding(ErrorCode code) {
        return switch (code) {
            case EMBEDDING_PROVIDER_UNAUTHORIZED -> EMBEDDING_PROVIDER_UNAUTHORIZED;
            case EMBEDDING_PROVIDER_UNAVAILABLE -> EMBEDDING_PROVIDER_UNAVAILABLE;
            case EMBEDDING_RATE_LIMITED -> EMBEDDING_RATE_LIMITED;
            case EMBEDDING_QUOTA_EXCEEDED -> EMBEDDING_QUOTA_EXCEEDED;
            case EMBEDDING_REQUEST_REJECTED -> EMBEDDING_REQUEST_REJECTED;
            default -> null;
        };
    }
}
