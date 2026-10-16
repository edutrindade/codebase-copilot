package com.codebasecopilot.providers;

import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// The only retry layer for provider calls (D-051): the OpenAI client's own retries are off.
public final class EmbeddingRetry {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static final Logger log = LoggerFactory.getLogger(EmbeddingRetry.class);

    private final int maxRetries;
    private final Duration initialBackoff;
    private final Duration maxWait;
    private final Sleeper sleeper;

    public EmbeddingRetry(
            int maxRetries, Duration initialBackoff, Duration maxWait, Sleeper sleeper) {
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
        this.maxWait = maxWait;
        this.sleeper = sleeper;
    }

    public <T> T call(Supplier<T> call) {
        return call(call, maxWait, () -> {});
    }

    // Waits in slices of at most waitSlice and runs onWait after each one, so a job can renew
    // its lease while waiting (D-040); an exception thrown by onWait stops the wait and escapes.
    public <T> T call(Supplier<T> call, Duration waitSlice, Runnable onWait) {
        for (int retry = 0; ; retry++) {
            try {
                return call.get();
            } catch (EmbeddingFailure failure) {
                if (!failure.retryable() || retry >= maxRetries) {
                    throw failure;
                }
                Duration wait = waitFor(failure, retry);
                log.warn(
                        "Embedding call failed with {} (HTTP {}); retry {} of {} in {} ms",
                        failure.type(),
                        failure.status(),
                        retry + 1,
                        maxRetries,
                        wait.toMillis());
                await(wait, waitSlice, onWait, failure);
            }
        }
    }

    private Duration waitFor(EmbeddingFailure failure, int retry) {
        Duration requested = failure.retryAfter();
        Duration wait =
                requested != null
                        ? requested
                        : initialBackoff.multipliedBy(1L << Math.min(retry, 30));
        return wait.compareTo(maxWait) > 0 ? maxWait : wait;
    }

    private void await(
            Duration wait, Duration waitSlice, Runnable onWait, EmbeddingFailure failure) {
        Duration remaining = wait;
        while (remaining.isPositive()) {
            Duration slice = remaining.compareTo(waitSlice) > 0 ? waitSlice : remaining;
            try {
                sleeper.sleep(slice);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw failure;
            }
            remaining = remaining.minus(slice);
            onWait.run();
        }
    }
}
