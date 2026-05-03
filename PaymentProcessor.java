package smartparking;

public interface PaymentProcessor {
    boolean processPayment(double amount) throws PaymentFrameworkException;
    String getPaymentMethodName();
    void validatePaymentDetails() throws PaymentFrameworkException;
    String generateReceipt();
}