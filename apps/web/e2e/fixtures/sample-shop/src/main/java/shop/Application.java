package shop;

public final class Application {

    private Application() {}

    public static void main(String[] args) {
        RefundService refunds = new RefundService(new FeePolicy(150));
        System.out.println(refunds.refund(new Order("o-1", 10_000)));
    }
}
