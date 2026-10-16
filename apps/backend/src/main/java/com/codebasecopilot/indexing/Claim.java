package com.codebasecopilot.indexing;

import java.util.UUID;

// A job taken from the queue; attempt guards every later transition (D-040).
record Claim(UUID jobId, UUID projectId, UUID snapshotId, int attempt) {}
