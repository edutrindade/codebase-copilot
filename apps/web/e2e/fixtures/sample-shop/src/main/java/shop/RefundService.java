package shop;

public final class RefundService {

    private final FeePolicy fees;

    public RefundService(FeePolicy fees) {
        this.fees = fees;
    }

    // The refund is the order total minus the processing fee.
    public long refund(Order order) {
        long fee = fees.feeFor(order);
        return order.totalCents() - fee;
    }
}
