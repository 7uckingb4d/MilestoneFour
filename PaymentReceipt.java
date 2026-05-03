package smartparking;

public class PaymentReceipt {
    
    private final boolean success;
    private final String transactionId;
    private final String buyerName;
    private final double amount;
    private final double remainingBalance;
    private final String timestamp;
    private final String message;

    private PaymentReceipt(boolean success, String transactionId, String buyerName,
                           double amount, double remainingBalance, String timestamp, String message) {
        this.success = success;
        this.transactionId = transactionId;
        this.buyerName = buyerName;
        this.amount = amount;
        this.remainingBalance = remainingBalance;
        this.timestamp = timestamp;
        this.message = message;
    }

    public static PaymentReceipt success(String txnId, String buyer, double amount,
                                         double remaining, String timestamp) {
        return new PaymentReceipt(true, txnId, buyer, amount, remaining, timestamp, "SUCCESS");
    }

    public static PaymentReceipt failed(String txnId, String buyer, double amount, String reason) {
        return new PaymentReceipt(false, txnId, buyer, amount, 0, null, reason);
    }

    public boolean isSuccess() { return success; }
    public String getTransactionId() { return transactionId; }
    public String getBuyerName() { return buyerName; }
    public double getAmount() { return amount; }
    public double getRemainingBalance() { return remainingBalance; }
    public String getTimestamp() { return timestamp; }
    public String getMessage() { return message; }
}