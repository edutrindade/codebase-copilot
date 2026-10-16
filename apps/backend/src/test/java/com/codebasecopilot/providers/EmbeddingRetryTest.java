package com.codebasecopilot.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.providers.EmbeddingFailure.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EmbeddingRetryTest {

    private static final Duration MAX_WAIT = Duration.ofSeconds(60);

    private final List<Duration> sleeps = new ArrayList<>();
    private final AtomicInteger attempts = new AtomicInteger();

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @ParameterizedTest
    @EnumSource(
            value = Type.class,
            names = {"UNAUTHORIZED", "INVALID_REQUEST"})
    void permanentFailuresAreNotRetried(Type type) {
        assertThatThrownBy(() -> retry(4).call(failing(type, null)))
                .isInstanceOfSatisfying(
                        EmbeddingFailure.class, e -> assertThat(e.type()).isEqualTo(type));

        assertThat(attempts).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = Type.class,
            names = {"RATE_LIMITED", "UNAVAILABLE"})
    void transientFailuresAreRetriedUpToMaxRetries(Type type) {
        assertThatThrownBy(() -> retry(4).call(failing(type, null)))
                .isInstanceOfSatisfying(
                        EmbeddingFailure.class, e -> assertThat(e.type()).isEqualTo(type));

        assertThat(attempts).hasValue(5);
    }

    @Test
    void zeroMaxRetriesMeansSingleAttempt() {
        assertThatThrownBy(() -> retry(0).call(failing(Type.UNAVAILABLE, null)))
                .isInstanceOf(EmbeddingFailure.class);

        assertThat(attempts).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void returnsResultAfterTransientFailures() {
        Supplier<String> call =
                () -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new EmbeddingFailure(Type.UNAVAILABLE, 503, null, "test");
                    }
                    return "ok";
                };

        assertThat(retry(4).call(call)).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void backoffGrowsExponentially() {
        assertThatThrownBy(() -> retry(4).call(failing(Type.UNAVAILABLE, null)))
                .isInstanceOf(EmbeddingFailure.class);

        assertThat(sleeps).containsExactly(seconds(1), seconds(2), seconds(4), seconds(8));
    }

    @Test
    void backoffIsCappedByMaxRetryWait() {
        EmbeddingRetry retry = new EmbeddingRetry(4, seconds(1), seconds(3), sleeps::add);

        assertThatThrownBy(() -> retry.call(failing(Type.UNAVAILABLE, null)))
                .isInstanceOf(EmbeddingFailure.class);

        assertThat(sleeps).containsExactly(seconds(1), seconds(2), seconds(3), seconds(3));
    }

    @Test
    void retryAfterIsHonored() {
        assertThatThrownBy(() -> retry(1).call(failing(Type.RATE_LIMITED, seconds(5))))
                .isInstanceOf(EmbeddingFailure.class);

        assertThat(sleeps).containsExactly(seconds(5));
    }

    @Test
    void retryAfterIsCappedByMaxRetryWait() {
        assertThatThrownBy(() -> retry(1).call(failing(Type.RATE_LIMITED, seconds(300))))
                .isInstanceOf(EmbeddingFailure.class);

        assertThat(sleeps).containsExactly(MAX_WAIT);
    }

    @Test
    void zeroRetryAfterRetriesWithoutWaiting() {
        assertThatThrownBy(() -> retry(1).call(failing(Type.RATE_LIMITED, Duration.ZERO)))
                .isInstanceOf(EmbeddingFailure.class);

        assertThat(attempts).hasValue(2);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void longWaitIsSlicedAndCallsHookAfterEachSlice() {
        AtomicInteger beats = new AtomicInteger();

        assertThatThrownBy(
                        () ->
                                retry(1).call(
                                                failing(Type.RATE_LIMITED, seconds(300)),
                                                seconds(25),
                                                beats::incrementAndGet))
                .isInstanceOf(EmbeddingFailure.class);

        assertThat(sleeps).containsExactly(seconds(25), seconds(25), seconds(10));
        assertThat(total(sleeps)).isEqualTo(MAX_WAIT);
        assertThat(beats).hasValue(3);
    }

    @Test
    void hookFailureStopsWaitingAndIsNotRetried() {
        AtomicInteger beats = new AtomicInteger();
        IllegalStateException lostClaim = new IllegalStateException("claim lost");
        Runnable hook =
                () -> {
                    if (beats.incrementAndGet() == 2) {
                        throw lostClaim;
                    }
                };

        assertThatThrownBy(
                        () ->
                                retry(4).call(
                                                failing(Type.RATE_LIMITED, seconds(60)),
                                                seconds(10),
                                                hook))
                .isSameAs(lostClaim);

        assertThat(sleeps).containsExactly(seconds(10), seconds(10));
        assertThat(attempts).hasValue(1);
    }

    @Test
    void interruptedWaitStopsWithTheFailure() {
        EmbeddingRetry retry =
                new EmbeddingRetry(
                        4,
                        seconds(1),
                        MAX_WAIT,
                        duration -> {
                            throw new InterruptedException();
                        });

        assertThatThrownBy(() -> retry.call(failing(Type.UNAVAILABLE, null)))
                .isInstanceOfSatisfying(
                        EmbeddingFailure.class,
                        e -> assertThat(e.type()).isEqualTo(Type.UNAVAILABLE));

        assertThat(attempts).hasValue(1);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void otherExceptionsAreNotRetried() {
        Supplier<String> call =
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalArgumentException("bug");
                };

        assertThatThrownBy(() -> retry(4).call(call)).isInstanceOf(IllegalArgumentException.class);
        assertThat(attempts).hasValue(1);
    }

    private EmbeddingRetry retry(int maxRetries) {
        return new EmbeddingRetry(maxRetries, seconds(1), MAX_WAIT, sleeps::add);
    }

    private Supplier<String> failing(Type type, @Nullable Duration retryAfter) {
        return () -> {
            attempts.incrementAndGet();
            throw new EmbeddingFailure(type, null, retryAfter, "test");
        };
    }

    private static Duration total(List<Duration> durations) {
        return durations.stream().reduce(Duration.ZERO, Duration::plus);
    }

    private static Duration seconds(long seconds) {
        return Duration.ofSeconds(seconds);
    }
}
