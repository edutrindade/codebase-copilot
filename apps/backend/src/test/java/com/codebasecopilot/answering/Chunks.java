package com.codebasecopilot.answering;

import com.codebasecopilot.retrieval.RetrievedChunk;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class Chunks {

    private Chunks() {}

    // One line of content per line number, "line <n> of <path>".
    static RetrievedChunk chunk(String path, int startLine, int endLine, int rank) {
        String content =
                IntStream.rangeClosed(startLine, endLine)
                        .mapToObj(n -> "line " + n + " of " + path)
                        .collect(Collectors.joining("\n", "", "\n"));
        return new RetrievedChunk(
                UUID.randomUUID(),
                List.of(path),
                "hash-" + path,
                startLine,
                endLine,
                content,
                (content.length() + 3) / 4,
                rank,
                rank,
                null,
                null);
    }

    static ContextEvidence evidence(String label, String path, int startLine, int endLine) {
        return new ContextEvidence(label, chunk(path, startLine, endLine, 1));
    }
}
