package com.codebasecopilot.indexing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;

// Keeps the lease of a claim alive and reports progress; throws LostClaimException when the job
// was taken over or removed, and IndexCancelled when a cancellation was requested (D-084).
final class Heartbeat {

    private final IndexJobRepository jobs;
    private final Claim claim;
    private final Clock clock;
    private final Duration interval;
    private IndexStage stage = IndexStage.SCANNING;
    private @Nullable Integer total;
    private @Nullable Integer completed;
    private Instant last = Instant.MIN;

    Heartbeat(IndexJobRepository jobs, Claim claim, Clock clock, Duration interval) {
        this.jobs = jobs;
        this.claim = claim;
        this.clock = clock;
        this.interval = interval;
    }

    void stage(IndexStage stage, @Nullable Integer total, @Nullable Integer completed) {
        this.stage = stage;
        this.total = total;
        this.completed = completed;
        beat();
    }

    void progress(int completed) {
        this.completed = completed;
        beat();
    }

    // Renews the lease while waiting, for instance between provider retries (D-051).
    void renew() {
        beat();
    }

    // Cheap enough to call per file: writes only once per interval.
    void tick() {
        if (Duration.between(last, clock.instant()).compareTo(interval) >= 0) {
            beat();
        }
    }

    private void beat() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!jobs.heartbeat(claim, stage, total, completed, now)) {
            if (jobs.cancelRequested(claim)) {
                throw new IndexCancelled();
            }
            throw new LostClaimException();
        }
        last = now;
    }
}
