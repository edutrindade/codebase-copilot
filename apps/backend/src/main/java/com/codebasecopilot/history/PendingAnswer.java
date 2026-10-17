package com.codebasecopilot.history;

import java.util.UUID;

public record PendingAnswer(UUID answerId, UUID conversationId, UUID questionMessageId) {}
