package smartparking;

import java.util.Scanner;

public class PaymentCLI {
    public static void processInvoiceFromUser() {
        Scanner sc = new Scanner(System.in);

        System.out.print("Buyer name: ");
        String buyer = sc.nextLine();

        System.out.print("Amount to pay: ");
        double amount = Double.parseDouble(sc.nextLine());

        System.out.print("GCash balance: ");
        double balance = Double.parseDouble(sc.nextLine());

        System.out.print("Discount rate (0 to 1): ");
        double discount = Double.parseDouble(sc.nextLine());

        GCashPayment payment = new GCashPayment(buyer, amount, balance, discount);
        PaymentProcessor processor = new SimpleProcessor();

        payment.processInvoice(processor);
    }
}