package com.codebasecopilot.retrieval;

import java.util.List;
import java.util.UUID;

// One chunk of a snapshot; paths lists every file of the snapshot with its content, in byte
// order (D-039). The score only orders the hits of one channel.
record SearchHit(
        UUID chunkId,
        List<String> paths,
        int startLine,
        int endLine,
        String content,
        double score) {}
