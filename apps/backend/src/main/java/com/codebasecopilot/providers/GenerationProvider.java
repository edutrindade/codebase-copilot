package com.codebasecopilot.providers;

// Port for chat models (D-010). Implementations throw GenerationFailure and never retry: a
// person is waiting for the answer (D-051).
public interface GenerationProvider {

    GenerationResult generate(GenerationRequest request);

    GenerationModel model();
}
