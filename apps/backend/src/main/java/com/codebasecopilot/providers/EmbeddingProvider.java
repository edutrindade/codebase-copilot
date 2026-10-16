package com.codebasecopilot.providers;

import java.util.List;

// Port for embedding models (D-010). Implementations throw EmbeddingFailure and never retry;
// retries belong to EmbeddingRetry (D-051).
public interface EmbeddingProvider {

    // One vector per text, in the same order, each with model().dimensions() components.
    EmbeddingResult embed(List<String> texts);

    ModelInfo model();
}
