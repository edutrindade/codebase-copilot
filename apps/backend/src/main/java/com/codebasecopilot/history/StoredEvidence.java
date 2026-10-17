package com.codebasecopilot.history;

import java.util.UUID;

public record StoredEvidence(UUID id, UUID snapshotId, AnswerEvidence evidence) {}
