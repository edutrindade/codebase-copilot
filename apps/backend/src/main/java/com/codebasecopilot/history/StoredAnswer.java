package com.codebasecopilot.history;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record StoredAnswer(
        UUID id,
        UUID projectId,
        UUID conversationId,
        String question,
        UUID snapshotId,
        AnswerStatus status,
        @Nullable String text,
        @Nullable String errorCode,
        List<String> warnings,
        int invalidCitationCount,
        String retrievalMode,
        String promptVersion,
        @Nullable String generationProvider,
        @Nullable String generationModel,
        @Nullable Map<String, Object> retrievalMetadata,
        AnswerMetrics metrics,
        Instant createdAt,
        @Nullable Instant completedAt,
        List<StoredEvidence> evidence) {

    public StoredAnswer {
        warnings = List.copyOf(warnings);
        evidence = List.copyOf(evidence);
    }

    StoredAnswer withEvidence(List<StoredEvidence> evidence) {
        return new StoredAnswer(
                id,
                projectId,
                conversationId,
                question,
                snapshotId,
                status,
                text,
                errorCode,
                warnings,
                invalidCitationCount,
                retrievalMode,
                promptVersion,
                generationProvider,
                generationModel,
                retrievalMetadata,
                metrics,
                createdAt,
                completedAt,
                evidence);
    }
}
