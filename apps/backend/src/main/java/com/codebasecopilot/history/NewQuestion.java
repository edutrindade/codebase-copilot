package com.codebasecopilot.history;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

// A null conversationId starts a new conversation.
public record NewQuestion(
        UUID projectId,
        @Nullable UUID conversationId,
        String question,
        UUID snapshotId,
        String retrievalMode,
        String promptVersion) {}
