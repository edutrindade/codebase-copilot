package com.codebasecopilot.answering;

import com.codebasecopilot.history.AnswerHistory;
import com.codebasecopilot.providers.GenerationProperties;
import com.codebasecopilot.providers.ProviderProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

// A PENDING answer older than the longest a request can wait on providers (question embedding
// plus generation) has lost its request; a recent one may belong to another running instance.
@Component
class AnswerRecovery implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(AnswerRecovery.class);

    private final AnswerHistory history;
    private final Duration maxRequestDuration;
    private final Clock clock;

    AnswerRecovery(
            AnswerHistory history,
            ProviderProperties embeddings,
            GenerationProperties generation,
            Clock clock) {
        this.history = history;
        this.maxRequestDuration = embeddings.timeout().plus(generation.timeout());
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        recover();
    }

    void recover() {
        Instant cutoff = clock.instant().minus(maxRequestDuration);
        int interrupted =
                history.failPendingCreatedBefore(cutoff, AnswerErrorCode.ANSWER_INTERRUPTED.name());
        if (interrupted > 0) {
            log.warn("Marked {} interrupted answers as failed", interrupted);
        }
    }
}
