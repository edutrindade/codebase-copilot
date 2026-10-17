package shop;

public final class FeePolicy {

    private final long processingFeeCents;

    public FeePolicy(long processingFeeCents) {
        this.processingFeeCents = processingFeeCents;
    }

    public long feeFor(Order order) {
        return Math.min(processingFeeCents, order.totalCents());
    }
}
