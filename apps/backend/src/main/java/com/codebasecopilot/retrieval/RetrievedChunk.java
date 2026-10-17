package com.codebasecopilot.retrieval;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// A retrieved chunk in final order: rank is its 1-based position, the channel ranks its
// position in each channel (null when the channel missed it). paths are every file of the
// snapshot with this content, in byte order (D-039).
public record RetrievedChunk(
        UUID chunkId,
        List<String> paths,
        String contentHash,
        int startLine,
        int endLine,
        String content,
        int tokenEstimate,
        int rank,
        @Nullable Integer rankLexical,
        @Nullable Integer rankVector,
        @Nullable Integer rankPath) {

    public RetrievedChunk {
        paths = List.copyOf(paths);
    }
}
