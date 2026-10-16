package com.example.billing;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class InvoiceCalculator {

    static final long LATE_FEE_CENTS_PER_DAY = 150;
    static final long MAX_LATE_FEE_CENTS = 5_000;
    static final int GRACE_PERIOD_DAYS = 5;

    private final Clock clock;

    public InvoiceCalculator(Clock clock) {
        this.clock = clock;
    }

    public long totalCents(Invoice invoice) {
        long subtotal = invoice.items().stream()
                .mapToLong(item -> item.unitPriceCents() * item.quantity())
                .sum();
        long discounted = applyDiscount(subtotal, invoice.discountPercent());
        return discounted + calculateLateFee(invoice.dueDate());
    }

    long applyDiscount(long subtotalCents, int discountPercent) {
        if (discountPercent < 0 || discountPercent > 100) {
            throw new IllegalArgumentException("discount must be between 0 and 100");
        }
        return subtotalCents - (subtotalCents * discountPercent / 100);
    }

    long calculateLateFee(LocalDate dueDate) {
        long daysLate = ChronoUnit.DAYS.between(dueDate, LocalDate.now(clock));
        if (daysLate <= GRACE_PERIOD_DAYS) {
            return 0;
        }
        long fee = (daysLate - GRACE_PERIOD_DAYS) * LATE_FEE_CENTS_PER_DAY;
        return Math.min(fee, MAX_LATE_FEE_CENTS);
    }

    public record Item(String sku, long unitPriceCents, int quantity) {}

    public record Invoice(java.util.List<Item> items, int discountPercent, LocalDate dueDate) {}
}
