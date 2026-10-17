package com.codebasecopilot.answering;

import com.codebasecopilot.retrieval.RetrievedChunk;

// A chunk chosen for the prompt; the model sees only the label, the first path, the lines and
// the text.
record ContextEvidence(String label, RetrievedChunk chunk) {

    String path() {
        return chunk.paths().getFirst();
    }
}
