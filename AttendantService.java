package smartparking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public final class AttendantService {

    private AttendantService() {}

    public static void portal(int attendantId, Scanner sc) {
        while (true) {
            System.out.println("\n--- ATTENDANT PORTAL ---");
            System.out.println("1. Dashboard");
            System.out.println("2. Spot Monitoring");
            System.out.println("3. Check Reservations");
            System.out.println("4. Walk-in Booking");
            System.out.println("5. Check-in Vehicle (by Plate)");
            System.out.println("6. Check-out & Billing (by Plate)");
            System.out.println("7. Logout");
            System.out.print("Choose: ");
            String c = sc.nextLine().trim();

            switch (c) {
                case "1" -> dashboard();
                case "2" -> spotMonitoring(sc);
                case "3" -> checkReservations(sc);
                case "4" -> walkInBooking(sc);
                case "5" -> checkInVehicle(sc);
                case "6" -> checkOutBilling(sc);
                case "7" -> { return; }
                default -> System.out.println("Invalid.");
            }
        }
    }

    public static void dashboard() {
        Main.countPrint("Pending Reservations", "SELECT COUNT(*) c FROM reservations WHERE status='PENDING'");
        Main.countPrint("Checked-in Vehicles", "SELECT COUNT(*) c FROM reservations WHERE status='CHECKED_IN'");
        Main.countPrint("Unpaid Billings", "SELECT COUNT(*) c FROM reservations WHERE billing_status='UNPAID' AND status IN ('CHECKED_IN','COMPLETED')");
    }

    public static void spotMonitoring(Scanner sc) {
        int zoneId = pickZoneDynamic(sc);
        if (zoneId == 0) return;
        Main.listSpots(zoneId);
        System.out.println("Press enter to return...");
        sc.nextLine();
    }

    public static void checkReservations(Scanner sc) {
        autoExpirePending();

        String listSql = "SELECT r.id, v.plate_number, z.zone_name, s.spot_code, r.start_time, r.end_time, r.payment_mode " +
                "FROM reservations r " +
                "JOIN vehicles v ON r.vehicle_id=v.id " +
                "JOIN parking_zones z ON r.zone_id=z.id " +
                "JOIN parking_spots s ON r.spot_id=s.id " +
                "WHERE r.status='PENDING' ORDER BY datetime(r.start_time) ASC";

        boolean has = false;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(listSql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n--- PENDING RESERVATIONS ---");
            while (rs.next()) {
                has = true;
                System.out.println("Res#" + rs.getInt("id")
                        + " | Plate:" + rs.getString("plate_number")
                        + " | " + rs.getString("zone_name") + " " + rs.getString("spot_code")
                        + " | " + rs.getString("start_time") + " -> " + rs.getString("end_time")
                        + " | " + rs.getString("payment_mode"));
            }
        } catch (SQLException e) {
            System.out.println("Load pending error: " + e.getMessage());
            return;
        }

        if (!has) {
            System.out.println("No pending reservations.");
            return;
        }

        System.out.print("Plate number (0 return): ");
        String plate = sc.nextLine().trim().toUpperCase();
        if ("0".equals(plate)) return;

        System.out.println("1. Accept latest pending");
        System.out.println("2. Reject latest pending");
        System.out.print("Choose: ");
        String act = sc.nextLine().trim();

        String findPendingByPlate =
                "SELECT r.id FROM reservations r " +
                "JOIN vehicles v ON r.vehicle_id=v.id " +
                "WHERE UPPER(v.plate_number)=UPPER(?) AND r.status='PENDING' " +
                "ORDER BY r.id DESC LIMIT 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement fp = conn.prepareStatement(findPendingByPlate)) {

            fp.setString(1, plate);
            try (ResultSet rr = fp.executeQuery()) {
                if (!rr.next()) {
                    System.out.println("No PENDING reservation found for plate " + plate);
                    return;
                }

                int reservationId = rr.getInt("id");
                if ("1".equals(act)) acceptReservation(reservationId);
                else if ("2".equals(act)) rejectReservation(reservationId);
                else System.out.println("Cancelled.");
            }

        } catch (SQLException e) {
            System.out.println("Find reservation error: " + e.getMessage());
        }
    }

    private static void acceptReservation(int reservationId) {
        String getSql = "SELECT spot_id,status FROM reservations WHERE id=?";
        String updSql = "UPDATE reservations SET status='CONFIRMED' WHERE id=? AND status='PENDING'";
        String spotSql = "UPDATE parking_spots SET status='RESERVED' WHERE id=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement g = conn.prepareStatement(getSql);
             PreparedStatement u = conn.prepareStatement(updSql);
             PreparedStatement s = conn.prepareStatement(spotSql)) {

            g.setInt(1, reservationId);
            try (ResultSet r = g.executeQuery()) {
                if (!r.next()) { System.out.println("Reservation not found."); return; }

                int spotId = r.getInt("spot_id");
                if (!"PENDING".equals(r.getString("status"))) {
                    System.out.println("Only PENDING can be accepted."); return;
                }

                u.setInt(1, reservationId);
                int ok = u.executeUpdate();
                if (ok > 0) {
                    s.setInt(1, spotId);
                    s.executeUpdate();
                    System.out.println("Accepted: CONFIRMED, spot RESERVED.");
                } else {
                    System.out.println("Accept failed.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Accept error: " + e.getMessage());
        }
    }

    private static void rejectReservation(int reservationId) {
        String getSql = "SELECT spot_id,status FROM reservations WHERE id=?";
        String updSql = "UPDATE reservations SET status='DENIED' WHERE id=? AND status='PENDING'";
        String spotSql = "UPDATE parking_spots SET status='AVAILABLE' WHERE id=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement g = conn.prepareStatement(getSql);
             PreparedStatement u = conn.prepareStatement(updSql);
             PreparedStatement s = conn.prepareStatement(spotSql)) {

            g.setInt(1, reservationId);
            try (ResultSet r = g.executeQuery()) {
                if (!r.next()) { System.out.println("Reservation not found."); return; }

                int spotId = r.getInt("spot_id");
                if (!"PENDING".equals(r.getString("status"))) {
                    System.out.println("Only PENDING can be rejected."); return;
                }

                u.setInt(1, reservationId);
                int ok = u.executeUpdate();
                if (ok > 0) {
                    s.setInt(1, spotId);
                    s.executeUpdate();
                    System.out.println("Rejected: DENIED, spot AVAILABLE.");
                } else {
                    System.out.println("Reject failed.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Reject error: " + e.getMessage());
        }
    }

    public static void walkInBooking(Scanner sc) {
        System.out.println("\n--- WALK-IN BOOKING ---");

        int zoneId = pickZoneDynamic(sc);
        if (zoneId == 0) return;

        String spotsSql = "SELECT spot_code,spot_type FROM parking_spots WHERE zone_id=? AND status='AVAILABLE' ORDER BY spot_code";
        boolean hasSpot = false;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(spotsSql)) {
            ps.setInt(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hasSpot = true;
                    System.out.println(rs.getString("spot_code") + " | " + rs.getString("spot_type"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Load spots error: " + e.getMessage());
            return;
        }

        if (!hasSpot) { System.out.println("No available spots in this zone."); return; }

        System.out.print("Spot code (e.g. A-01, 0 return): ");
        String spotCode = sc.nextLine().trim().toUpperCase();
        if ("0".equals(spotCode)) return;

        System.out.print("Plate number: ");
        String plate = sc.nextLine().trim().toUpperCase();
        if (plate.isEmpty()) { System.out.println("Plate required."); return; }

        if (hasActiveReservationByPlate(plate)) {
            System.out.println("This plate already has active reservation/parking.");
            return;
        }

        int hours = Main.readInt(sc, "Planned hours: ", false);
        if (hours <= 0) { System.out.println("Invalid hours."); return; }

        String findVehicle = "SELECT id,customer_id FROM vehicles WHERE UPPER(plate_number)=UPPER(?) LIMIT 1";
        String createGuestVehicle = "INSERT INTO vehicles(customer_id,plate_number,make,model,color,is_guest) VALUES(NULL,?,?,?, ?,1)";
        String getSpotRate = "SELECT s.id, r.hourly_rate FROM parking_spots s JOIN parking_rates r ON s.spot_type=r.parking_type " +
                "WHERE s.zone_id=? AND UPPER(s.spot_code)=UPPER(?) AND s.status='AVAILABLE'";
        String insertReservation = "INSERT INTO reservations(customer_id,vehicle_id,zone_id,spot_id,start_time,end_time,check_in_time,status,payment_mode,reservation_fee,hourly_rate_snapshot,created_by_attendant,billing_status) " +
                "VALUES(?,?,?,?,datetime('now'),datetime('now','+' || ? || ' hours'),datetime('now'),'CHECKED_IN','CHECKOUT_PAY',?, ?,1,'UNPAID')";
        String occupySpot = "UPDATE parking_spots SET status='OCCUPIED' WHERE id=? AND status='AVAILABLE'";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement f = conn.prepareStatement(findVehicle);
             PreparedStatement gv = conn.prepareStatement(createGuestVehicle);
             PreparedStatement rate = conn.prepareStatement(getSpotRate);
             PreparedStatement ins = conn.prepareStatement(insertReservation);
             PreparedStatement occ = conn.prepareStatement(occupySpot)) {

            Integer vehicleId = null;
            Integer customerId = null;

            f.setString(1, plate);
            try (ResultSet vr = f.executeQuery()) {
                if (vr.next()) {
                    vehicleId = vr.getInt("id");
                    customerId = vr.getObject("customer_id") == null ? null : vr.getInt("customer_id");
                }
            }

            if (vehicleId == null) {
                gv.setString(1, plate);
                gv.setString(2, "GUEST");
                gv.setString(3, "GUEST");
                gv.setString(4, "N/A");
                gv.executeUpdate();

                f.setString(1, plate);
                try (ResultSet vr2 = f.executeQuery()) {
                    if (vr2.next()) vehicleId = vr2.getInt("id");
                    else { System.out.println("Failed to create guest vehicle."); return; }
                }
            }

            rate.setInt(1, zoneId);
            rate.setString(2, spotCode);

            int spotId;
            double hourlyRate;
            try (ResultSet rr = rate.executeQuery()) {
                if (!rr.next()) { System.out.println("Invalid/unavailable spot code."); return; }
                spotId = rr.getInt("id");
                hourlyRate = rr.getDouble("hourly_rate");
            }

            occ.setInt(1, spotId);
            if (occ.executeUpdate() == 0) { System.out.println("Spot is no longer available."); return; }

            double plannedParking = hours * hourlyRate;
            double reservationFee = plannedParking * 0.20;

            if (customerId == null) ins.setNull(1, java.sql.Types.INTEGER);
            else ins.setInt(1, customerId);
            ins.setInt(2, vehicleId);
            ins.setInt(3, zoneId);
            ins.setInt(4, spotId);
            ins.setInt(5, hours);
            ins.setDouble(6, reservationFee);
            ins.setDouble(7, hourlyRate);

            int ok = ins.executeUpdate();
            System.out.println(ok > 0 ? "Walk-in booked & checked-in." : "Walk-in booking failed.");

        } catch (SQLException e) {
            System.out.println("Walk-in error: " + e.getMessage());
        }
    }
            public static void checkInVehicle(Scanner sc) {
        System.out.println("\n--- CHECK-IN VEHICLE (BY PLATE) ---");

        String listSql = "SELECT r.id, v.plate_number, z.zone_name, s.spot_code, r.start_time, r.end_time " +
                "FROM reservations r " +
                "JOIN vehicles v ON r.vehicle_id=v.id " +
                "JOIN parking_zones z ON r.zone_id=z.id " +
                "JOIN parking_spots s ON r.spot_id=s.id " +
                "WHERE r.status='CONFIRMED' ORDER BY r.id DESC";

        boolean has = false;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(listSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                has = true;
                System.out.println("Res#" + rs.getInt("id")
                        + " | Plate:" + rs.getString("plate_number")
                        + " | " + rs.getString("zone_name") + " " + rs.getString("spot_code")
                        + " | " + rs.getString("start_time") + " -> " + rs.getString("end_time"));
            }
        } catch (SQLException e) {
            System.out.println("Load check-in list error: " + e.getMessage());
            return;
        }

        if (!has) {
            System.out.println("No CONFIRMED reservations.");
            return;
        }

        System.out.print("Plate number to check-in (0 return): ");
        String plate = sc.nextLine().trim().toUpperCase();
        if ("0".equals(plate)) return;

        if (isPlateCheckedIn(plate)) {
            System.out.println("Plate already checked-in / parked.");
            return;
        }

        String getSql = "SELECT r.id, r.spot_id FROM reservations r " +
                "JOIN vehicles v ON r.vehicle_id=v.id " +
                "WHERE UPPER(v.plate_number)=UPPER(?) AND r.status='CONFIRMED' " +
                "ORDER BY r.id DESC LIMIT 1";
        String updRes = "UPDATE reservations SET status='CHECKED_IN', check_in_time=datetime('now') WHERE id=? AND status='CONFIRMED'";
        String updSpot = "UPDATE parking_spots SET status='OCCUPIED' WHERE id=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement g = conn.prepareStatement(getSql);
             PreparedStatement u = conn.prepareStatement(updRes);
             PreparedStatement s = conn.prepareStatement(updSpot)) {

            g.setString(1, plate);
            try (ResultSet r = g.executeQuery()) {
                if (!r.next()) {
                    System.out.println("No CONFIRMED reservation for plate " + plate);
                    return;
                }

                int resId = r.getInt("id");
                int spotId = r.getInt("spot_id");

                u.setInt(1, resId);
                int ok = u.executeUpdate();
                if (ok > 0) {
                    s.setInt(1, spotId);
                    s.executeUpdate();
                    System.out.println("Checked-in: " + plate);
                } else {
                    System.out.println("Check-in failed.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Check-in error: " + e.getMessage());
        }
    }

    public static void checkOutBilling(Scanner sc) {
    System.out.println("\n--- CHECK-OUT & BILLING (BY PLATE) ---");

    String listSql = "SELECT r.id, v.plate_number, r.payment_mode, r.check_in_time, r.end_time, r.hourly_rate_snapshot, r.reservation_fee " +
            "FROM reservations r JOIN vehicles v ON r.vehicle_id=v.id " +
            "WHERE r.status='CHECKED_IN' ORDER BY datetime(r.check_in_time) ASC";

    boolean has = false;
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(listSql);
         ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            has = true;
            String now = nowSqlText();
            long parkedH = Math.max(1, ceilHoursBetween(rs.getString("check_in_time"), now));
            long overH = Math.max(0, ceilHoursBetween(rs.getString("end_time"), now));
            double rate = rs.getDouble("hourly_rate_snapshot");
            double resFee = rs.getDouble("reservation_fee");
            String mode = rs.getString("payment_mode");

            double preview = "PAY_NOW".equals(mode)
                    ? (overH * 10.0) // only overstay due now
                    : (resFee + parkedH * rate + overH * 10.0);

            System.out.println("Res#" + rs.getInt("id")
                    + " | Plate:" + rs.getString("plate_number")
                    + " | Mode:" + mode
                    + " | EstTotalNow:" + preview);
        }
    } catch (SQLException e) {
        System.out.println("Load checkout list error: " + e.getMessage());
        return;
    }

    if (!has) {
        System.out.println("No CHECKED_IN reservations.");
        return;
    }

    System.out.print("Plate number to checkout (0 return): ");
    String plate = sc.nextLine().trim().toUpperCase();
    if ("0".equals(plate)) return;

    String getSql = "SELECT r.id, r.spot_id, r.customer_id, r.check_in_time, r.end_time, " +
            "r.hourly_rate_snapshot, r.reservation_fee, r.payment_mode " +
            "FROM reservations r JOIN vehicles v ON r.vehicle_id=v.id " +
            "WHERE UPPER(v.plate_number)=UPPER(?) AND r.status='CHECKED_IN' " +
            "ORDER BY r.id DESC LIMIT 1";

    String updRes = "UPDATE reservations SET check_out_time=datetime('now'), status='COMPLETED', " +
            "parking_fee=?, overstay_fee=?, total_amount=?, billing_status='PAID' " +
            "WHERE id=? AND status='CHECKED_IN'";
    String freeSpot = "UPDATE parking_spots SET status='AVAILABLE' WHERE id=?";
    String payLog = "INSERT INTO payments(reservation_id,customer_id,amount,payment_method) VALUES(?,?,?,'CASH')";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement g = conn.prepareStatement(getSql);
         PreparedStatement u = conn.prepareStatement(updRes);
         PreparedStatement f = conn.prepareStatement(freeSpot);
         PreparedStatement p = conn.prepareStatement(payLog)) {

        g.setString(1, plate);
        try (ResultSet r = g.executeQuery()) {
            if (!r.next()) {
                System.out.println("No CHECKED_IN reservation for plate " + plate);
                return;
            }

            int resId = r.getInt("id");
            int spotId = r.getInt("spot_id");
            Integer customerId = r.getObject("customer_id") == null ? null : r.getInt("customer_id");
            String in = r.getString("check_in_time");
            String end = r.getString("end_time");
            double rate = r.getDouble("hourly_rate_snapshot");
            double resFee = r.getDouble("reservation_fee");
            String mode = r.getString("payment_mode");

            String now = nowSqlText();
            long parkedHours = Math.max(1, ceilHoursBetween(in, now));
            long overstayHours = Math.max(0, ceilHoursBetween(end, now));

            double parkingFee = parkedHours * rate;
            double overstayFee = overstayHours * 10.0;
            double totalNow;

            if ("PAY_NOW".equals(mode)) {
                // already paid at reservation creation (reservation fee + planned parking)
                parkingFee = 0.0;
                totalNow = overstayFee;
            } else {
                totalNow = resFee + parkingFee + overstayFee;
            }

            u.setDouble(1, parkingFee);
            u.setDouble(2, overstayFee);
            u.setDouble(3, totalNow);
            u.setInt(4, resId);

            int ok = u.executeUpdate();
            if (ok > 0) {
                f.setInt(1, spotId);
                f.executeUpdate();

                // LOG PAYMENT ONLY IF MAY BABAYARAN NOW
                if (totalNow > 0.0001) {
                    p.setInt(1, resId);
                    if (customerId == null) p.setNull(2, java.sql.Types.INTEGER);
                    else p.setInt(2, customerId);
                    p.setDouble(3, totalNow);
                    p.executeUpdate();
                }

                System.out.println("Checkout complete.");
                System.out.println("Mode: " + mode);
                if ("PAY_NOW".equals(mode)) {
                    System.out.println("Base parking already paid.");
                } else {
                    System.out.println("Reservation Fee: " + resFee);
                    System.out.println("Parking Fee(" + parkedHours + "h x " + rate + "): " + parkingFee);
                }
                System.out.println("Overstay Fee(" + overstayHours + "h x 10): " + overstayFee);
                System.out.println("TOTAL PAID NOW: " + totalNow);
            } else {
                System.out.println("Checkout failed.");
            }
        }

    } catch (SQLException e) {
        System.out.println("Checkout error: " + e.getMessage());
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
            return true; // fail-safe block
        }
    }

    private static boolean isPlateCheckedIn(String plate) {
        String sql = "SELECT 1 FROM reservations r " +
                "JOIN vehicles v ON r.vehicle_id=v.id " +
                "WHERE UPPER(v.plate_number)=UPPER(?) AND r.status='CHECKED_IN' LIMIT 1";
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

    private static void autoExpirePending() {
        String sql = "UPDATE reservations SET status='EXPIRED' " +
                "WHERE status='PENDING' AND datetime(end_time) < datetime('now')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private static String nowSqlText() {
        return java.time.LocalDateTime.now().toString().replace("T", " ");
    }

    private static long ceilHoursBetween(String from, String to) {
        try {
            java.time.LocalDateTime a = java.time.LocalDateTime.parse(from.replace(" ", "T"));
            java.time.LocalDateTime b = java.time.LocalDateTime.parse(to.replace(" ", "T"));
            long mins = java.time.Duration.between(a, b).toMinutes();
            if (mins <= 0) return 0;
            return (long) Math.ceil(mins / 60.0);
        } catch (Exception e) {
            return 0;
        }
    }
}
   
   

