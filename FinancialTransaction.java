package smartparking;

import java.time.LocalDateTime;

public class FinancialTransaction {
    public enum Type { CHARGE, REFUND }

    private final String reservationId;
    private final Type type;
    private final double amount;
    private final String method;
    private final LocalDateTime timestamp;

    public FinancialTransaction(String reservationId, Type type, double amount, String method) {
        this.reservationId = reservationId;
        this.type = type;
        this.amount = amount;
        this.method = method;
        this.timestamp = LocalDateTime.now();
    }

    public String getReservationId() { return reservationId; }
    public Type getType() { return type; }
    public double getAmount() { return amount; }
    public String getMethod() { return method; }
    public LocalDateTime getTimestamp() { return timestamp; }
}