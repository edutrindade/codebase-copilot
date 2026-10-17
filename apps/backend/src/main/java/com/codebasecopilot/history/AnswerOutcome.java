package com.codebasecopilot.history;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

// The text becomes the ASSISTANT message; a FAILED answer has none. retrievalMetadata never
// holds question, answer or code text.
public record AnswerOutcome(
        AnswerStatus status,
        @Nullable String text,
        @Nullable String errorCode,
        List<String> warnings,
        int invalidCitationCount,
        @Nullable String generationProvider,
        @Nullable String generationModel,
        @Nullable Map<String, Object> retrievalMetadata,
        AnswerMetrics metrics,
        List<AnswerEvidence> evidence) {

    public AnswerOutcome {
        if (status == AnswerStatus.PENDING) {
            throw new IllegalArgumentException("An outcome cannot be PENDING");
        }
        warnings = List.copyOf(warnings);
        evidence = List.copyOf(evidence);
    }
}
