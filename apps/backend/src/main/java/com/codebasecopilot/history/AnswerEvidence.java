package com.codebasecopilot.history;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

// Copy of an evidence sent to the model, kept after its snapshot or chunk is gone (D-046).
public record AnswerEvidence(
        @Nullable UUID chunkId,
        String citationLabel,
        String relativePath,
        List<String> otherPaths,
        String contentHash,
        int startLine,
        int endLine,
        @Nullable Integer citedStartLine,
        @Nullable Integer citedEndLine,
        String snippet,
        @Nullable Integer rankLexical,
        @Nullable Integer rankVector,
        @Nullable Integer rankPath,
        int finalRank,
        EvidenceValidation validation) {

    public AnswerEvidence {
        otherPaths = List.copyOf(otherPaths);
    }
}
