package com.codebasecopilot.indexing;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Table queue (D-040): one dispatcher thread claims jobs with SKIP LOCKED and hands them to a
// fixed pool. Jobs left RUNNING by a crash are claimed again once their lease expires.
@Component
@EnableConfigurationProperties(IndexingProperties.class)
class IndexWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(IndexWorker.class);

    private final IndexJobRepository jobs;
    private final IndexOutcomes outcomes;
    private final IndexRunner runner;
    private final TransactionTemplate transactions;
    private final IndexingProperties properties;
    private final Clock clock;
    private volatile Semaphore slots = new Semaphore(0);
    private volatile @Nullable ScheduledExecutorService dispatcher;
    private volatile @Nullable ExecutorService workers;
    private volatile boolean running;

    IndexWorker(
            IndexJobRepository jobs,
            IndexOutcomes outcomes,
            IndexRunner runner,
            TransactionTemplate transactions,
            IndexingProperties properties,
            Clock clock) {
        this.jobs = jobs;
        this.outcomes = outcomes;
        this.runner = runner;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    // Executors are created on every start: the test framework stops and restarts the
    // lifecycle of cached contexts.
    @Override
    public synchronized void start() {
        slots = new Semaphore(properties.maxConcurrent());
        workers =
                Executors.newFixedThreadPool(
                        properties.maxConcurrent(),
                        Thread.ofPlatform().name("index-", 0).daemon().factory());
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().name("index-dispatcher").daemon().factory());
        dispatcher = scheduler;
        running = true;
        long poll = properties.pollInterval().toMillis();
        scheduler.scheduleWithFixedDelay(this::dispatch, 0, poll, TimeUnit.MILLISECONDS);
    }

    // Interrupted jobs stay RUNNING and are taken again once their lease expires.
    @Override
    public synchronized void stop() {
        running = false;
        if (dispatcher != null) {
            dispatcher.shutdownNow();
        }
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    void signal() {
        ScheduledExecutorService scheduler = dispatcher;
        if (running && scheduler != null) {
            try {
                scheduler.execute(this::dispatch);
            } catch (RejectedExecutionException e) {
                // Stopping; the next start polls the queue.
            }
        }
    }

    // Runs only on the dispatcher thread.
    private void dispatch() {
        Semaphore slots = this.slots;
        ExecutorService pool = workers;
        while (running && pool != null && slots.tryAcquire()) {
            Optional<Claim> claim;
            try {
                claim = claimNext();
            } catch (RuntimeException e) {
                slots.release();
                log.warn("Could not claim an index job: {}", e.getClass().getSimpleName());
                return;
            }
            if (claim.isEmpty()) {
                slots.release();
                return;
            }
            try {
                pool.execute(
                        () -> {
                            try {
                                runner.run(claim.get());
                            } finally {
                                slots.release();
                                signal();
                            }
                        });
            } catch (RejectedExecutionException e) {
                // Stopping: the claimed job stays RUNNING until its lease expires.
                slots.release();
                return;
            }
        }
    }

    // Jobs that already used every attempt (the worker died each time) fail instead of starting;
    // jobs whose cancellation was requested before a crash or a requeue end as CANCELLED.
    Optional<Claim> claimNext() {
        while (true) {
            Optional<Optional<Claim>> result =
                    Optional.ofNullable(
                            transactions.execute(
                                    tx -> {
                                        Instant now = now();
                                        Optional<IndexJobRepository.Locked> locked =
                                                jobs.lockNext(now.minus(properties.leaseTimeout()));
                                        if (locked.isEmpty()) {
                                            return Optional.<Claim>empty();
                                        }
                                        Claim current = locked.get().claim();
                                        if (locked.get().cancelRequested()) {
                                            outcomes.cancel(current);
                                            return null;
                                        }
                                        if (current.attempt() >= properties.maxAttempts()) {
                                            outcomes.fail(
                                                    current,
                                                    IndexErrorCode.INDEX_ATTEMPTS_EXHAUSTED);
                                            return null;
                                        }
                                        jobs.start(current, now);
                                        return Optional.of(
                                                new Claim(
                                                        current.jobId(),
                                                        current.projectId(),
                                                        current.snapshotId(),
                                                        current.attempt() + 1));
                                    }));
            if (result.isPresent()) {
                return result.get();
            }
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
