package com.codebasecopilot.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// candidates: how many hits each channel fetches before the hybrid fusion (D-059).
@ConfigurationProperties("app.retrieval")
record RetrievalProperties(@DefaultValue("50") int candidates) {

    RetrievalProperties {
        if (candidates < 1) {
            throw new IllegalArgumentException("app.retrieval.candidates must be positive");
        }
    }
}
