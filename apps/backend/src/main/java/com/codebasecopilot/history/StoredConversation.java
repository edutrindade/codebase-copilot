package com.codebasecopilot.history;

import java.time.Instant;
import java.util.UUID;

public record StoredConversation(
        UUID id,
        UUID projectId,
        String title,
        int questionCount,
        Instant createdAt,
        Instant updatedAt) {}
