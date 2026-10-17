package com.example.payments;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PaymentWebhookHandler {

    private static final Duration KEY_RETENTION = Duration.ofHours(24);

    private final Map<String, Instant> seenKeys = new ConcurrentHashMap<>();
    private final LedgerClient ledger;

    public PaymentWebhookHandler(LedgerClient ledger) {
        this.ledger = ledger;
    }

    public Outcome handle(WebhookEvent event, Instant receivedAt) {
        evictExpired(receivedAt);
        Instant previous = seenKeys.putIfAbsent(event.idempotencyKey(), receivedAt);
        if (previous != null) {
            return Outcome.DUPLICATE_IGNORED;
        }
        ledger.record(event.invoiceId(), event.amountCents());
        return Outcome.APPLIED;
    }

    private void evictExpired(Instant now) {
        seenKeys.values().removeIf(seen -> seen.plus(KEY_RETENTION).isBefore(now));
    }

    public enum Outcome {
        APPLIED,
        DUPLICATE_IGNORED
    }

    public record WebhookEvent(String idempotencyKey, String invoiceId, long amountCents) {}

    public interface LedgerClient {
        void record(String invoiceId, long amountCents);
    }
}
