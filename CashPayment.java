package smartparking;

/**
 * CONCRETE CLASS - CashPayment
 * Extends PaymentFramework - Demonstrates Inheritance
 * Implements all abstract methods with specific cash payment logic
 * Uses Encapsulation, Composition, and Polymorphism
 */
public class CashPayment extends PaymentFramework {
    
    // ENCAPSULATION: Private final fields for immutability
    private final double paymentAmount;
    private final String cashier;
    private boolean isSettled;
    
    /**
     * CONSTRUCTOR - Demonstrates super() for parent initialization
     * Uses THIS KEYWORD for clarity
     */
    public CashPayment(String buyerName, double rawAmount, double creditBalance,
                       double discountRate, double paymentAmount, String cashier) {
        super(buyerName, rawAmount, creditBalance, discountRate, "CASH");
        this.paymentAmount = paymentAmount;
        this.cashier = cashier;
        this.isSettled = false;
    }
    
    /**
     * IMPLEMENTATION OF ABSTRACT METHOD
     * Validates payment specific to cash payment
     */
    @Override
    public void validatePayment() throws PaymentFrameworkException {
        if (paymentAmount <= 0) {
            throw new PaymentFrameworkException("Payment amount must be greater than zero");
        }
        if (paymentAmount < this.rawAmount) {
            throw new PaymentFrameworkException("Insufficient payment amount");
        }
    }
    
    /**
     * IMPLEMENTATION OF ABSTRACT METHOD
     * Applies VAT to the payment
     */
    @Override
    public double applyVat() throws PaymentFrameworkException {
        try {
            double discountedAmount = applyDiscount();
            return discountedAmount * this.vatRate;
        } catch (PaymentFrameworkException e) {
            throw new PaymentFrameworkException("VAT calculation failed", e);
        }
    }
    
    /**
     * IMPLEMENTATION OF ABSTRACT METHOD
     * Applies discount to the payment
     */
    @Override
    public double applyDiscount() throws PaymentFrameworkException {
        try {
            return this.rawAmount * (1 - this.discountRate);
        } catch (Exception e) {
            throw new PaymentFrameworkException("Discount calculation failed", e);
        }
    }
    
    /**
     * IMPLEMENTATION OF ABSTRACT METHOD
     * Finalizes cash transaction
     */
    @Override
    public void finalizeTransaction() throws PaymentFrameworkException {
        try {
            validatePayment();
            this.isSettled = true;
            System.out.println("=== CASH TRANSACTION FINALIZED ===");
            System.out.println("Transaction ID: " + this.transactionId);
            System.out.println("Buyer: " + this.buyerName);
            System.out.println("Amount Paid: " + this.paymentAmount);
            System.out.println("Timestamp: " + this.invoiceTimestamp);
        } catch (PaymentFrameworkException e) {
            throw e;
        }
    }
    
    /**
     * OVERLOADING - Multiple processInvoice methods
     * Process invoice with default processor
     * 
     * @return PaymentReceipt containing transaction details
     */
    public PaymentReceipt processInvoice() {
        return processInvoice(new SimpleProcessor());
    }
    
    /**
     * OVERLOADING - Multiple processInvoice methods
     * Process invoice with specific processor
     * POLYMORPHISM - Accepts PaymentProcessor interface
     * 
     * @param processor the payment processor to use for processing
     * @return PaymentReceipt containing transaction details and status
     */
    public PaymentReceipt processInvoice(PaymentProcessor processor) {
        try {
            System.out.println("\n=== PROCESSING INVOICE ===");
            System.out.println("Processor: " + processor.getPaymentMethodName());
            processor.validatePaymentDetails();
            
            boolean success = processor.processPayment(this.paymentAmount);
            
            if (success) {
                this.finalizeTransaction();
                System.out.println(processor.generateReceipt());
                return PaymentReceipt.success(this.transactionId, this.buyerName, this.paymentAmount, 
                                             0, this.invoiceTimestamp);
            } else {
                throw new PaymentFrameworkException("Payment processing failed");
            }
        } catch (PaymentFrameworkException e) {
            return PaymentReceipt.failed(this.transactionId, this.buyerName, this.paymentAmount, e.getMessage());
        } finally {
            System.out.println("=== INVOICE PROCESSING COMPLETED ===\n");
        }
    }
    
    /**
     * ACCESSOR - Data hiding with controlled access
     */
    public double getPaymentAmount() {
        return this.paymentAmount;
    }
    
    public String getCashier() {
        return this.cashier;
    }
    
    public boolean isSettled() {
        return this.isSettled;
    }
    
    /**
     * IMMUTABLE PATTERN - Returns new instance instead of modifying
     */
    public CashPayment withNewDiscount(double newDiscount) {
        return new CashPayment(this.buyerName, this.rawAmount, this.creditBalance,
                              newDiscount, this.paymentAmount, this.cashier);
    }
}
