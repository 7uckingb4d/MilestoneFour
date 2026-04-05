package smartparking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public final class CustomerService {

    private CustomerService() {}

    public static void portal(int customerId, Scanner sc) {
        while (true) {
            System.out.println("\n--- CUSTOMER PORTAL ---");
            System.out.println("1. Dashboard");
            System.out.println("2. Reservations");
            System.out.println("3. My Vehicles");
            System.out.println("4. My Payments");
            System.out.println("5. Update Profile");
            System.out.println("6. Logout");
            System.out.print("Choose: ");
            String c = sc.nextLine().trim();

            switch (c) {
                case "1" -> dashboard(customerId);
                case "2" -> reservations(customerId, sc);
                case "3" -> vehicles(customerId, sc);
                case "4" -> myPayments(customerId);
                case "5" -> Main.updateProfile(customerId, sc);
                case "6" -> { return; }
                default -> System.out.println("Invalid.");
            }
        }
    }

    public static void dashboard(int customerId) {
        System.out.println("\nActive Reservations:");
        String rsql = "SELECT r.id,z.zone_name,s.spot_code,r.start_time,r.end_time,r.status,r.payment_mode,r.billing_status,r.total_amount " +
                "FROM reservations r JOIN parking_zones z ON r.zone_id=z.id " +
                "JOIN parking_spots s ON r.spot_id=s.id " +
                "WHERE r.customer_id=? AND r.status IN ('PENDING','CONFIRMED','CHECKED_IN') ORDER BY r.id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(rsql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean has = false;
                while (rs.next()) {
                    has = true;
                    System.out.println("Res#" + rs.getInt("id")
                            + " | " + rs.getString("zone_name") + " - " + rs.getString("spot_code")
                            + " | " + rs.getString("start_time") + " -> " + rs.getString("end_time")
                            + " | " + rs.getString("status")
                            + " | " + rs.getString("payment_mode")
                            + " | Bill:" + rs.getString("billing_status")
                            + " | Amount:" + rs.getDouble("total_amount"));
                }
                if (!has) System.out.println("- none");
            }
        } catch (SQLException e) {
            System.out.println("Dashboard error: " + e.getMessage());
        }
    }

    public static void reservations(int customerId, Scanner sc) {
        while (true) {
            System.out.println("\n--- RESERVATIONS ---");
            System.out.println("1. Create Reservation");
            System.out.println("2. List My Reservations");
            System.out.println("3. Return");
            System.out.print("Choose: ");
            String c = sc.nextLine().trim();

            if ("1".equals(c)) createReservation(customerId, sc);
            else if ("2".equals(c)) listMyReservations(customerId);
            else if ("3".equals(c)) return;
            else System.out.println("Invalid.");
        }
    }

    public static void createReservation(int customerId, Scanner sc) {
        System.out.println("\n--- CREATE RESERVATION ---");

        listVehicles(customerId);
        System.out.print("Plate number (0 return): ");
        String plateInput = sc.nextLine().trim().toUpperCase();
        if ("0".equals(plateInput)) return;

        String vSql = "SELECT id FROM vehicles WHERE customer_id=? AND UPPER(plate_number)=UPPER(?) LIMIT 1";
        int vehicleId;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(vSql)) {
            ps.setInt(1, customerId);
            ps.setString(2, plateInput);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Plate not found under your account.");
                    return;
                }
                vehicleId = rs.getInt("id");
            }
        } catch (SQLException e) {
            System.out.println("Vehicle lookup error: " + e.getMessage());
            return;
        }

        if (hasActiveReservationByPlate(plateInput)) {
            System.out.println("This plate already has active reservation/parking.");
            return;
        }

        int zoneId = pickZoneDynamic(sc);
        if (zoneId == 0) return;

        String spotListSql = "SELECT spot_code, spot_type FROM parking_spots WHERE zone_id=? AND status='AVAILABLE' ORDER BY spot_code";
        boolean hasSpot = false;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(spotListSql)) {
            ps.setInt(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hasSpot = true;
                    System.out.println(rs.getString("spot_code") + " | Type:" + rs.getString("spot_type"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Load spots error: " + e.getMessage());
            return;
        }
        if (!hasSpot) {
            System.out.println("No available spots in this zone.");
            return;
        }

        System.out.print("Spot code (e.g. A-01, 0 return): ");
        String spotCode = sc.nextLine().trim().toUpperCase();
        if ("0".equals(spotCode)) return;

        int hours = Main.readInt(sc, "Planned hours: ", false);
        if (hours <= 0) {
            System.out.println("Invalid hours.");
            return;
        }

        System.out.println("Payment Mode:");
        System.out.println("1. Pay Now");
        System.out.println("2. Checkout Pay");
        System.out.print("Choose: ");
        String pm = sc.nextLine().trim();

        String paymentMode;
        String billingStatus;
        if ("1".equals(pm)) {
            paymentMode = "PAY_NOW";
            billingStatus = "PAID";
        } else if ("2".equals(pm)) {
            paymentMode = "CHECKOUT_PAY";
            billingStatus = "UNPAID";
        } else {
            System.out.println("Invalid payment mode.");
            return;
        }

        String spotRateSql = "SELECT s.id, r.hourly_rate " +
                "FROM parking_spots s " +
                "JOIN parking_rates r ON s.spot_type=r.parking_type " +
                "WHERE s.zone_id=? AND UPPER(s.spot_code)=UPPER(?) AND s.status='AVAILABLE'";

        String reserveSpotSql = "UPDATE parking_spots SET status='RESERVED' WHERE id=? AND status='AVAILABLE'";

        String insSql = "INSERT INTO reservations " +
                "(customer_id,vehicle_id,zone_id,spot_id,start_time,end_time,status,payment_mode,reservation_fee,hourly_rate_snapshot,billing_status,created_by_attendant) " +
                "VALUES (?,?,?,?,datetime('now'),datetime('now','+' || ? || ' hours'),'PENDING',?,?,?, ?,0)";

        String findResSql = "SELECT id FROM reservations WHERE customer_id=? ORDER BY id DESC LIMIT 1";
        String payLogSql = "INSERT INTO payments(reservation_id,customer_id,amount,payment_method) VALUES(?,?,?,'CASH')";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement sr = conn.prepareStatement(spotRateSql);
             PreparedStatement rsu = conn.prepareStatement(reserveSpotSql);
             PreparedStatement ins = conn.prepareStatement(insSql);
             PreparedStatement fr = conn.prepareStatement(findResSql);
             PreparedStatement pl = conn.prepareStatement(payLogSql)) {

            sr.setInt(1, zoneId);
            sr.setString(2, spotCode);

            int spotId;
            double hourlyRate;
            try (ResultSet rr = sr.executeQuery()) {
                if (!rr.next()) {
                    System.out.println("Invalid/unavailable spot code.");
                    return;
                }
                spotId = rr.getInt("id");
                hourlyRate = rr.getDouble("hourly_rate");
            }

            rsu.setInt(1, spotId);
            int spotOk = rsu.executeUpdate();
            if (spotOk == 0) {
                System.out.println("Spot already taken.");
                return;
            }

            double plannedParking = hours * hourlyRate;
            double reservationFee = plannedParking * 0.20; // 20% fee

            ins.setInt(1, customerId);
            ins.setInt(2, vehicleId);
            ins.setInt(3, zoneId);
            ins.setInt(4, spotId);
            ins.setInt(5, hours);
            ins.setString(6, paymentMode);
            ins.setDouble(7, reservationFee);
            ins.setDouble(8, hourlyRate);
            ins.setString(9, billingStatus);

            int ok = ins.executeUpdate();
            if (ok <= 0) {
                System.out.println("Create reservation failed.");
                return;
            }

            if ("PAY_NOW".equals(paymentMode)) {
                double initialAmount = reservationFee + plannedParking;

                fr.setInt(1, customerId);
                int reservationId = -1;
                try (ResultSet x = fr.executeQuery()) {
                    if (x.next()) reservationId = x.getInt("id");
                }

                if (reservationId > 0) {
                    pl.setInt(1, reservationId);
                    pl.setInt(2, customerId);
                    pl.setDouble(3, initialAmount);
                    pl.executeUpdate();
                }

                System.out.println("Reservation created (PENDING). PAY NOW received: " + initialAmount);
            } else {
                System.out.println("Reservation created (PENDING). Pay at checkout.");
            }

        } catch (SQLException e) {
            System.out.println("Create reservation error: " + e.getMessage());
        }
    }

    public static void listMyReservations(int customerId) {
        String sql = "SELECT r.id, z.zone_name, s.spot_code, s.spot_type, " +
                "r.start_time, r.end_time, r.status, r.payment_mode, r.billing_status, r.total_amount " +
                "FROM reservations r " +
                "JOIN parking_zones z ON r.zone_id = z.id " +
                "JOIN parking_spots s ON r.spot_id = s.id " +
                "WHERE r.customer_id=? ORDER BY r.id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean has = false;
                while (rs.next()) {
                    has = true;
                    System.out.println("Res#" + rs.getInt("id")
                            + " | " + rs.getString("zone_name")
                            + " | " + rs.getString("spot_code")
                            + " | " + rs.getString("spot_type")
                            + " | " + rs.getString("start_time") + " -> " + rs.getString("end_time")
                            + " | " + rs.getString("status")
                            + " | " + rs.getString("payment_mode")
                            + " | Billing:" + rs.getString("billing_status")
                            + " | Total:" + rs.getDouble("total_amount"));
                }
                if (!has) System.out.println("- none");
            }
        } catch (SQLException e) {
            System.out.println("List reservation error: " + e.getMessage());
        }
    }

    public static void vehicles(int customerId, Scanner sc) {
        while (true) {
            System.out.println("\n--- MY VEHICLES ---");
            System.out.println("1. Add");
            System.out.println("2. Update");
            System.out.println("3. Remove");
            System.out.println("4. List");
            System.out.println("5. Return");
            System.out.print("Choose: ");
            String c = sc.nextLine().trim();

            switch (c) {
                case "1" -> addVehicle(customerId, sc);
                case "2" -> updateVehicle(customerId, sc);
                case "3" -> removeVehicle(customerId, sc);
                case "4" -> listVehicles(customerId);
                case "5" -> { return; }
                default -> System.out.println("Invalid.");
            }
        }
    }

    public static void addVehicle(int customerId, Scanner sc) {
        System.out.print("Plate: ");
        String plate = sc.nextLine().trim().toUpperCase();
        if (plate.isEmpty()) { System.out.println("Plate required."); return; }

        System.out.print("Make: ");
        String make = sc.nextLine().trim();
        System.out.print("Model: ");
        String model = sc.nextLine().trim();
        System.out.print("Color: ");
        String color = sc.nextLine().trim();

        String sql = "INSERT INTO vehicles(customer_id,plate_number,make,model,color,is_guest) VALUES(?,?,?,?,?,0)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setString(2, plate);
            ps.setString(3, make);
            ps.setString(4, model);
            ps.setString(5, color);
            System.out.println(ps.executeUpdate() > 0 ? "Vehicle added." : "Failed.");
        } catch (SQLException e) {
            System.out.println("Add vehicle error: " + e.getMessage());
        }
    }

    public static void updateVehicle(int customerId, Scanner sc) {
        listVehicles(customerId);
        int id = Main.readInt(sc, "Vehicle ID (0 to return): ", true);
        if (id == 0) return;

        System.out.print("New Make: ");
        String make = sc.nextLine().trim();
        System.out.print("New Model: ");
        String model = sc.nextLine().trim();
        System.out.print("New Color: ");
        String color = sc.nextLine().trim();

        String sql = "UPDATE vehicles SET make=?,model=?,color=? WHERE id=? AND customer_id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, make);
            ps.setString(2, model);
            ps.setString(3, color);
            ps.setInt(4, id);
            ps.setInt(5, customerId);
            System.out.println(ps.executeUpdate() > 0 ? "Vehicle updated." : "Not found.");
        } catch (SQLException e) {
            System.out.println("Update vehicle error: " + e.getMessage());
        }
    }

    public static void removeVehicle(int customerId, Scanner sc) {
        listVehicles(customerId);
        int id = Main.readInt(sc, "Vehicle ID (0 to return): ", true);
        if (id == 0) return;

        String sql = "DELETE FROM vehicles WHERE id=? AND customer_id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, customerId);
            System.out.println(ps.executeUpdate() > 0 ? "Vehicle removed." : "Not found.");
        } catch (SQLException e) {
            System.out.println("Remove vehicle error: " + e.getMessage());
        }
    }

    public static void listVehicles(int customerId) {
        String sql = "SELECT id,plate_number,make,model,color FROM vehicles WHERE customer_id=? ORDER BY id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean has = false;
                while (rs.next()) {
                    has = true;
                    System.out.println(rs.getInt("id") + ". " + rs.getString("plate_number")
                            + " | " + rs.getString("make") + " " + rs.getString("model")
                            + " | " + rs.getString("color"));
                }
                if (!has) System.out.println("- no vehicles");
            }
        } catch (SQLException e) {
            System.out.println("List vehicle error: " + e.getMessage());
        }
    }

    public static void myPayments(int customerId) {
        System.out.println("\n--- PAYMENT HISTORY ---");
        String sql = "SELECT id,reservation_id,ticket_id,amount,payment_method,paid_at " +
                "FROM payments WHERE customer_id=? ORDER BY id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean has = false;
                while (rs.next()) {
                    has = true;
                    System.out.println("Pay#" + rs.getInt("id")
                            + " | Res:" + rs.getString("reservation_id")
                            + " | Ticket:" + rs.getString("ticket_id")
                            + " | Amount:" + rs.getDouble("amount")
                            + " | " + rs.getString("payment_method")
                            + " | " + rs.getString("paid_at"));
                }
                if (!has) System.out.println("- none");
            }
        } catch (SQLException e) {
            System.out.println("Payment list error: " + e.getMessage());
        }
    }

    private static int pickZoneDynamic(Scanner sc) {
        while (true) {
            System.out.println("Available Zones:");
            Main.listZones();
            System.out.print("Zone name or Zone ID (0 return): ");
            String input = sc.nextLine().trim();
            if ("0".equals(input)) return 0;

            String sql = "SELECT id FROM parking_zones WHERE active=1 AND (CAST(id AS TEXT)=? OR UPPER(zone_name)=UPPER(?)) LIMIT 1";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, input);
                ps.setString(2, input);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("id");
                    System.out.println("Zone not found/inactive.");
                }
            } catch (SQLException e) {
                System.out.println("Zone lookup error: " + e.getMessage());
                return 0;
            }
        }
    }

    private static boolean hasActiveReservationByPlate(String plate) {
        String sql = "SELECT 1 FROM reservations r " +
                "JOIN vehicles v ON r.vehicle_id=v.id " +
                "WHERE UPPER(v.plate_number)=UPPER(?) " +
                "AND r.status IN ('PENDING','CONFIRMED','CHECKED_IN') LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, plate);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return true;
        }
    }
}
