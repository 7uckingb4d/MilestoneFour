package smartparking;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class AdminService {

    public static void portal(int adminId, Scanner sc) {
        while (true) {
            System.out.println("\n--- ADMIN PORTAL ---");
            System.out.println("1. Update Profile");
            System.out.println("2. User Management");
            System.out.println("3. Parking Configuration");
            System.out.println("4. Adjust Pricing");
            System.out.println("5. Analytics Overview");
            System.out.println("6. Logout");
            System.out.print("Choose: ");
            String c = sc.nextLine();

            switch (c) {
                case "1" -> Main.updateProfile(adminId, sc);
                case "2" -> userManagement(sc);
                case "3" -> parkingConfig(sc);
                case "4" -> adjustPricing(sc);
                case "5" -> analyticsOverview(sc);
                case "6" -> { return; }
                default -> System.out.println("Invalid.");
            }
        }
    }

    public static void userManagement(Scanner sc) {
        while (true) {
            System.out.println("\n--- USER MANAGEMENT ---");
            String list = "SELECT id,name,email,role,status FROM users ORDER BY id";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(list);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getInt("id") + " | " + rs.getString("name")
                            + " | " + rs.getString("email")
                            + " | " + rs.getString("role")
                            + " | " + rs.getString("status"));
                }
            } catch (SQLException e) {
                System.out.println("List users error: " + e.getMessage());
            }

            System.out.println("1. Create User");
            System.out.println("2. Suspend User");
            System.out.println("3. Block User");
            System.out.println("4. Reactivate User");
            System.out.println("5. Remove User");
            System.out.println("6. Return");
            System.out.print("Choose: ");
            String c = sc.nextLine();

            switch (c) {
                case "1" -> createUser(sc);
                case "2" -> updateUserStatus("SUSPENDED", sc);
                case "3" -> updateUserStatus("BLOCKED", sc);
                case "4" -> updateUserStatus("ACTIVE", sc);
                case "5" -> removeUser(sc);
                case "6" -> { return; }
                default -> System.out.println("Invalid.");
            }
        }
    }

    public static void createUser(Scanner sc) {
        System.out.print("Name: ");
        String name = sc.nextLine().trim();
        System.out.print("Email: ");
        String email = sc.nextLine().trim();
        System.out.print("Password: ");
        String pass = sc.nextLine();
        System.out.print("Role (CUSTOMER/ATTENDANT/ADMIN): ");
        String role = sc.nextLine().trim().toUpperCase();

        User u = new User.Builder()
                .name(name)
                .email(email)
                .passwordHash(PasswordUtil.hashPassword(pass))
                .role(role)
                .status("ACTIVE")
                .build();

        String sql = "INSERT INTO users(name,email,password_hash,role,status) VALUES(?,?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u.getName());
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getPasswordHash());
            ps.setString(4, u.getRole());
            ps.setString(5, u.getStatus());
            System.out.println(ps.executeUpdate() > 0 ? "User created." : "Failed.");
        } catch (SQLException e) {
            System.out.println("Create user error: " + e.getMessage());
        }
    }

    public static void updateUserStatus(String status, Scanner sc) {
        System.out.print("User ID: ");
        int id = Integer.parseInt(sc.nextLine().trim());

        String mapped = "ACTIVE".equals(status) ? "ACTIVE" : "INACTIVE";

        String sql = "UPDATE users SET status=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mapped);
            ps.setInt(2, id);
            System.out.println(ps.executeUpdate() > 0 ? "Updated." : "Not found.");
        } catch (SQLException e) {
            System.out.println("Status update error: " + e.getMessage());
        }
    }

    public static void removeUser(Scanner sc) {
        System.out.print("User ID: ");
        int id = Integer.parseInt(sc.nextLine().trim());

        String sql = "UPDATE users SET status='INACTIVE' WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0 ? "User removed (soft delete)." : "Not found.");
        } catch (SQLException e) {
            System.out.println("Remove user error: " + e.getMessage());
        }
    }

    // ==========================
    // QUICK FIX: PARKING CONFIG
    // ==========================
    public static void parkingConfig(Scanner sc) {
        while (true) {
            System.out.println("\n--- PARKING CONFIGURATION ---");
            System.out.println("1. Open/Close Zone");
            System.out.println("2. Add Zone");
            System.out.println("3. Remove Zone");
            System.out.println("4. Open/Close Spot");
            System.out.println("5. Add Spot");
            System.out.println("6. Remove Spot");
            System.out.println("7. Edit Spot");
            System.out.println("8. Return");
            System.out.print("Choose: ");
            String c = sc.nextLine();

            switch (c) {
                case "1" -> openCloseZone(sc);
                case "2" -> addZone(sc);
                case "3" -> deactivateZone(sc);
                case "4" -> openCloseSpot(sc);
                case "5" -> addSpot(sc);
                case "6" -> removeSpot(sc);
                case "7" -> editSpotType(sc);
                case "8" -> { return; }
                default -> System.out.println("Invalid.");
            }
        }
    }

    // OPEN/CLOSE ZONE (active 1/0)
    public static void openCloseZone(Scanner sc) {
        Main.listZones();
        System.out.print("Zone ID: ");
        int id = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Open zone? (true/false): ");
        boolean open = Boolean.parseBoolean(sc.nextLine().trim());

        String sql = "UPDATE parking_zones SET active=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, open ? 1 : 0);
            ps.setInt(2, id);
            System.out.println(ps.executeUpdate() > 0 ? "Zone updated." : "Not found.");
        } catch (SQLException e) {
            System.out.println("Zone update error: " + e.getMessage());
        }
    }

    // ADD ZONE (user input)
    public static void addZone(Scanner sc) {
        System.out.print("Zone name (e.g., Zone C): ");
        String name = sc.nextLine().trim();
        if (name.isEmpty()) {
            System.out.println("Zone name required.");
            return;
        }

        String sql = "INSERT INTO parking_zones(zone_name,active) VALUES(?,1)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            System.out.println(ps.executeUpdate() > 0 ? "Zone added." : "Failed.");
        } catch (SQLException e) {
            System.out.println("Add zone error: " + e.getMessage());
        }
    }

    // REMOVE ZONE (soft delete in DB => active=0)
    public static void deactivateZone(Scanner sc) {
        Main.listZones();
        System.out.print("Zone ID to remove (set inactive): ");
        int id = Integer.parseInt(sc.nextLine().trim());

        String sql = "UPDATE parking_zones SET active=0 WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            System.out.println(ps.executeUpdate() > 0 ? "Zone removed (inactive)." : "Not found.");
        } catch (SQLException e) {
            System.out.println("Deactivate zone error: " + e.getMessage());
        }
    }

    // OPEN/CLOSE SPOT = restrict/open
    // We use MAINTENANCE as "closed/restricted"
    // Only allow toggling if spot is currently AVAILABLE or MAINTENANCE
    public static void openCloseSpot(Scanner sc) {
        Main.listZones();
        System.out.print("Zone ID: ");
        int zoneId = Integer.parseInt(sc.nextLine().trim());
        Main.listSpots(zoneId);

        System.out.print("Spot ID: ");
        int spotId = Integer.parseInt(sc.nextLine().trim());

        System.out.print("Set OPEN? (true=open/false=close): ");
        boolean open = Boolean.parseBoolean(sc.nextLine().trim());

        String newStatus = open ? "AVAILABLE" : "MAINTENANCE";

        String sql = "UPDATE parking_spots SET status=? WHERE id=? AND zone_id=? AND status IN ('AVAILABLE','MAINTENANCE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, spotId);
            ps.setInt(3, zoneId);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("Spot updated -> " + newStatus);
            } else {
                System.out.println("Cannot update spot. (Maybe RESERVED/OCCUPIED or not found)");
            }
        } catch (SQLException e) {
            System.out.println("Spot open/close error: " + e.getMessage());
        }
    }

    // ADD SPOT (user input)
    public static void addSpot(Scanner sc) {
        Main.listZones();
        System.out.print("Zone ID: ");
        int zoneId = Integer.parseInt(sc.nextLine().trim());

        System.out.print("Spot Code (e.g., A-11): ");
        String code = sc.nextLine().trim().toUpperCase();

        System.out.print("Spot Type (NORMAL/EV/PWD_SENIOR/VIP): ");
        String type = sc.nextLine().trim().toUpperCase();

        if (code.isEmpty()) {
            System.out.println("Spot code required.");
            return;
        }

        String sql = "INSERT INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(?,?,?,'AVAILABLE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, zoneId);
            ps.setString(2, code);
            ps.setString(3, type);
            System.out.println(ps.executeUpdate() > 0 ? "Spot added." : "Failed.");
        } catch (SQLException e) {
            System.out.println("Add spot error: " + e.getMessage());
        }
    }

    // REMOVE SPOT (hard delete row)
    // Restrict: cannot delete if RESERVED/OCCUPIED
    public static void removeSpot(Scanner sc) {
        Main.listZones();
        System.out.print("Zone ID: ");
        int zoneId = Integer.parseInt(sc.nextLine().trim());
        Main.listSpots(zoneId);

        System.out.print("Spot ID to remove: ");
        int spotId = Integer.parseInt(sc.nextLine().trim());

        String sql = "DELETE FROM parking_spots WHERE id=? AND zone_id=? AND status IN ('AVAILABLE','MAINTENANCE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, spotId);
            ps.setInt(2, zoneId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("Spot removed.");
            } else {
                System.out.println("Cannot remove spot. (Maybe RESERVED/OCCUPIED or not found)");
            }
        } catch (SQLException e) {
            System.out.println("Remove spot error: " + e.getMessage());
        }
    }

    // EDIT SPOT = change type (NORMAL/EV/PWD_SENIOR/VIP)
    // Restrict: do not edit if RESERVED/OCCUPIED
    public static void editSpotType(Scanner sc) {
        Main.listZones();
        System.out.print("Zone ID: ");
        int zoneId = Integer.parseInt(sc.nextLine().trim());
        Main.listSpots(zoneId);

        System.out.print("Spot ID: ");
        int spotId = Integer.parseInt(sc.nextLine().trim());

        System.out.print("New Spot Type (NORMAL/EV/PWD_SENIOR/VIP): ");
        String type = sc.nextLine().trim().toUpperCase();

        String sql = "UPDATE parking_spots SET spot_type=? WHERE id=? AND zone_id=? AND status IN ('AVAILABLE','MAINTENANCE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setInt(2, spotId);
            ps.setInt(3, zoneId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("Spot type updated.");
            } else {
                System.out.println("Cannot edit spot. (Maybe RESERVED/OCCUPIED or not found)");
            }
        } catch (SQLException e) {
            System.out.println("Edit spot error: " + e.getMessage());
        }
    }

    // ==========================
    // PRICING + ANALYTICS (unchanged)
    // ==========================
    public static void adjustPricing(Scanner sc) {
        while (true) {
            System.out.println("\n--- ADJUST PRICING ---");
            System.out.println("1. Parking Type Rates");
            System.out.println("2. Violation Fees");
            System.out.println("3. Return");
            System.out.print("Choose: ");
            String c = sc.nextLine();

            switch (c) {
                case "1" -> adjustParkingRates(sc);
                case "2" -> adjustViolationFees(sc);
                case "3" -> { return; }
                default -> System.out.println("Invalid.");
            }
        }
    }

    public static void adjustParkingRates(Scanner sc) {
        while (true) {
            System.out.println("\n--- PARKING TYPE RATES ---");
            String showSql = "SELECT parking_type, hourly_rate FROM parking_rates ORDER BY parking_type";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(showSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString("parking_type") + " = " + rs.getDouble("hourly_rate"));
                }
            } catch (SQLException e) {
                System.out.println("Show rates error: " + e.getMessage());
            }

            System.out.println("1. Add/Update Rate");
            System.out.println("2. Return");
            System.out.print("Choose: ");
            String c = sc.nextLine();
            if ("2".equals(c)) return;
            if (!"1".equals(c)) { System.out.println("Invalid."); continue; }

            System.out.print("Parking Type (NORMAL/EV/PWD_SENIOR/VIP): ");
            String type = sc.nextLine().trim().toUpperCase();
            System.out.print("Hourly Rate: ");
            double rate = Double.parseDouble(sc.nextLine().trim());

            String upsert = "INSERT INTO parking_rates(parking_type, hourly_rate) VALUES(?, ?) " +
                    "ON CONFLICT(parking_type) DO UPDATE SET hourly_rate=excluded.hourly_rate";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(upsert)) {
                ps.setString(1, type);
                ps.setDouble(2, rate);
                System.out.println(ps.executeUpdate() > 0 ? "Rate saved." : "Failed.");
            } catch (SQLException e) {
                System.out.println("Save rate error: " + e.getMessage());
            }
        }
    }

    public static void adjustViolationFees(Scanner sc) {
        ensureViolationRulesTable();

        while (true) {
            Main.listViolationRules();
            System.out.println("1. Add/Update Rule");
            System.out.println("2. Return");
            System.out.print("Choose: ");
            String c = sc.nextLine();

            if ("2".equals(c)) return;
            if (!"1".equals(c)) { System.out.println("Invalid."); continue; }

            System.out.print("Violation Type: ");
            String type = sc.nextLine().trim().toUpperCase();
            System.out.print("Fine Amount: ");
            double fine = Double.parseDouble(sc.nextLine().trim());

            String sql = "INSERT INTO violation_rules(violation_type,fine_amount) VALUES(?,?) " +
                    "ON CONFLICT(violation_type) DO UPDATE SET fine_amount=excluded.fine_amount";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, type);
                ps.setDouble(2, fine);
                System.out.println(ps.executeUpdate() > 0 ? "Saved." : "Failed.");
            } catch (SQLException e) {
                System.out.println("Save rule error: " + e.getMessage());
            }
        }
    }

    public static void analyticsOverview(Scanner sc) {
        System.out.println("\n--- ANALYTICS OVERVIEW ---");
        Main.countPrint("Total Reservations", "SELECT COUNT(*) c FROM reservations");
        Main.countPrint("Total Violations", "SELECT COUNT(*) c FROM tickets");
        Main.countPrint("Unpaid Tickets", "SELECT COUNT(*) c FROM tickets WHERE status='UNPAID'");

        System.out.println("1. Export Revenue CSV");
        System.out.println("2. Export Violation CSV");
        System.out.println("3. Export Utilization CSV");
        System.out.println("4. Return");
        System.out.print("Choose: ");
        String c = sc.nextLine();

        switch (c) {
            case "1" -> exportRevenueCSV();
            case "2" -> exportViolationCSV();
            case "3" -> exportUtilCSV();
            case "4" -> {}
            default -> System.out.println("Invalid.");
        }
    }

    public static void exportRevenueCSV() {
        String sql = "SELECT id,fine_amount,status FROM tickets WHERE status='PAID'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery();
             FileWriter fw = new FileWriter("revenue_report.csv")) {
            fw.write("ticket_id,amount,status\n");
            while (rs.next()) {
                fw.write(rs.getInt("id") + "," + rs.getDouble("fine_amount") + "," + rs.getString("status") + "\n");
            }
            System.out.println("Exported revenue_report.csv");
        } catch (SQLException | IOException e) {
            System.out.println("Export revenue error: " + e.getMessage());
        }
    }

    public static void exportViolationCSV() {
        String sql = "SELECT id,plate_number,violation_type,fine_amount,status FROM tickets";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery();
             FileWriter fw = new FileWriter("violation_report.csv")) {
            fw.write("id,plate_number,violation_type,fine_amount,status\n");
            while (rs.next()) {
                fw.write(rs.getInt("id") + "," + rs.getString("plate_number") + ","
                        + rs.getString("violation_type") + "," + rs.getDouble("fine_amount")
                        + "," + rs.getString("status") + "\n");
            }
            System.out.println("Exported violation_report.csv");
        } catch (SQLException | IOException e) {
            System.out.println("Export violation error: " + e.getMessage());
        }
    }

    public static void exportUtilCSV() {
        String sql = "SELECT z.zone_name,COUNT(s.id) total_spots,SUM(CASE WHEN s.status='OCCUPIED' THEN 1 ELSE 0 END) occupied " +
                "FROM parking_zones z LEFT JOIN parking_spots s ON z.id=s.zone_id GROUP BY z.id,z.zone_name";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery();
             FileWriter fw = new FileWriter("utilization_report.csv")) {
            fw.write("zone,total_spots,occupied\n");
            while (rs.next()) {
                fw.write(rs.getString("zone_name") + "," + rs.getInt("total_spots")
                        + "," + rs.getInt("occupied") + "\n");
            }
            System.out.println("Exported utilization_report.csv");
        } catch (SQLException | IOException e) {
            System.out.println("Export utilization error: " + e.getMessage());
        }
    }

    private static void ensureViolationRulesTable() {
        String ddl = """
            CREATE TABLE IF NOT EXISTS violation_rules (
                violation_type TEXT PRIMARY KEY,
                fine_amount REAL NOT NULL CHECK(fine_amount >= 0)
            )
            """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(ddl)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
}
