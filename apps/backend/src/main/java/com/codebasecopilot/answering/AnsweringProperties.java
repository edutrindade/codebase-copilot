package com.codebasecopilot.answering;

import com.codebasecopilot.retrieval.Retrieval;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Context limits of an answer (P4 of Etapa 6). Defaults are hypotheses measured in the answer
// evaluation.
@ConfigurationProperties("app.answering")
record AnsweringProperties(
        @DefaultValue("20") int candidates,
        @DefaultValue("6000") int maxContextTokens,
        @DefaultValue("3") int maxEvidencePerFile) {

    AnsweringProperties {
        if (candidates < 1 || candidates > Retrieval.MAX_CANDIDATES) {
            throw invalid("candidates must be between 1 and " + Retrieval.MAX_CANDIDATES);
        }
        if (maxContextTokens < 1) {
            throw invalid("max-context-tokens must be positive");
        }
        if (maxEvidencePerFile < 1) {
            throw invalid("max-evidence-per-file must be positive");
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("app.answering." + message);
    }
}
