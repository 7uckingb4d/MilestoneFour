package smartparking;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public abstract class PaymentFramework {
    protected String transactionId;
    protected String buyerName;
    protected double rawAmount;
    protected double creditBalance;
    protected final double vatRate = 0.12;
    protected double discountRate = 0.0;
    protected String paymentChannel;
    protected boolean isSettled;
    protected String invoiceTimestamp;

    public PaymentFramework(String buyerName, double rawAmount, double creditBalance,
                            double discountRate, String paymentChannel) {
        this.transactionId = UUID.randomUUID().toString();
        this.buyerName = buyerName;
        this.rawAmount = rawAmount;
        this.creditBalance = creditBalance;
        this.discountRate = discountRate;
        this.paymentChannel = paymentChannel;
        this.isSettled = false;
    }

    public abstract void validatePayment() throws PaymentFrameworkException;
    public abstract double applyVat() throws PaymentFrameworkException;
    public abstract double applyDiscount() throws PaymentFrameworkException;
    public abstract void finalizeTransaction() throws PaymentFrameworkException;

    protected double computeFinalAmount() throws PaymentFrameworkException {
        double discountedAmount = applyDiscount();
        double vatAmount = discountedAmount * vatRate;
        return discountedAmount + vatAmount;
    }

    public PaymentReceipt processInvoice(PaymentProcessor processor) {
        try {
            invoiceTimestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ISO_DATE_TIME);

            validatePayment();
            double finalAmount = computeFinalAmount();

            boolean success = processor.processPayment(finalAmount);

            if (success) {
                finalizeTransaction();
                System.out.println("\n=== TRANSACTION SUCCESS ===");
                System.out.println("Buyer: " + buyerName);
                System.out.println("Final Amount: " + finalAmount);
                System.out.println("Remaining Balance: " + creditBalance);
                System.out.println("Receipt: " + generateReceiptCode());
                return PaymentReceipt.success(transactionId, buyerName, finalAmount, 
                                             creditBalance, invoiceTimestamp);
            } else {
                System.out.println("\n=== TRANSACTION FAILED ===");
                return PaymentReceipt.failed(transactionId, buyerName, 0, "Payment processor declined");
            }
        } catch (PaymentFrameworkException e) {
            System.out.println("\n" + e.getMessage());
            return PaymentReceipt.failed(transactionId, buyerName, 0, e.getMessage());
        }
    }

    protected String generateReceiptCode() {
        return transactionId + "-" + invoiceTimestamp;
    }

    public boolean isSettled() {
        return isSettled;
    }

    public void resetTransaction() {
        isSettled = false;
        transactionId = null;
    }
}