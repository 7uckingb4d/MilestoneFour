package smartparking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class Main {
    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        DBConnection.init();

        while (true) {
            System.out.println("\n=== SMART PARKING SYSTEM ===");
            System.out.println("1. Register Customer");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Choose: ");
            String choice = sc.nextLine();

            switch (choice) {
                case "1" -> registerCustomer();
                case "2" -> login();
                case "3" -> { System.out.println("Bye."); return; }
                default -> System.out.println("Invalid.");
            }
        }
    }

    public static void registerCustomer() {
        System.out.println("\n--- REGISTER CUSTOMER ---");
        System.out.println("Type 0 anytime to cancel.");

        System.out.print("Name: ");
        String name = sc.nextLine().trim();
        if ("0".equals(name)) return;

        System.out.print("Email: ");
        String email = sc.nextLine().trim();
        if ("0".equals(email)) return;

        System.out.print("Password: ");
        String pass = sc.nextLine();
        if ("0".equals(pass)) return;

        User u = new User.Builder()
                .name(name).email(email)
                .passwordHash(PasswordUtil.hashPassword(pass))
                .role("CUSTOMER").status("ACTIVE")
                .build();

        String sql = "INSERT INTO users(name,email,password_hash,role,status) VALUES(?,?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u.getName());
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getPasswordHash());
            ps.setString(4, u.getRole());
            ps.setString(5, u.getStatus());
            System.out.println(ps.executeUpdate() > 0 ? "Registered." : "Failed.");
        } catch (SQLException e) {
            System.out.println("Register error: " + e.getMessage());
        }
    }

    public static void login() {
        System.out.print("Email: ");
        String email = sc.nextLine().trim();
        System.out.print("Password: ");
        String password = sc.nextLine();

        String sql = "SELECT id,name,role,status,password_hash FROM users WHERE email=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { System.out.println("Not found."); return; }

                if (!"ACTIVE".equals(rs.getString("status"))) {
                    System.out.println("Account not active."); return;
                }

                if (!PasswordUtil.hashPassword(password).equals(rs.getString("password_hash"))) {
                    System.out.println("Wrong password."); return;
                }

                int userId = rs.getInt("id");
                String name = rs.getString("name");
                String role = rs.getString("role");
                System.out.println("Welcome, " + name + " [" + role + "]");

                switch (role) {
                    case "CUSTOMER" -> CustomerService.portal(userId, sc);
                    case "ATTENDANT" -> AttendantService.portal(userId, sc);
                    case "ADMIN" -> AdminService.portal(userId, sc);
                    default -> System.out.println("Unknown role.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Login error: " + e.getMessage());
        }
    }

    public static void updateProfile(int userId, Scanner sc) {
        System.out.print("Enter old password: ");
        String oldPassword = sc.nextLine();

        String checkSql = "SELECT password_hash FROM users WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { System.out.println("User not found."); return; }
                if (!PasswordUtil.hashPassword(oldPassword).equals(rs.getString("password_hash"))) {
                    System.out.println("Wrong old password. Return."); return;
                }
            }

            System.out.println("1. Change Name");
            System.out.println("2. Change Email");
            System.out.println("3. Change Password");
            System.out.println("4. Return");
            System.out.print("Choose: ");
            String choice = sc.nextLine();

            switch (choice) {
                case "1" -> {
                    System.out.print("New name: ");
                    execUpdate("UPDATE users SET name=? WHERE id=?", sc.nextLine().trim(), userId);
                    System.out.println("Name updated.");
                }
                case "2" -> {
                    System.out.print("New email: ");
                    execUpdate("UPDATE users SET email=? WHERE id=?", sc.nextLine().trim(), userId);
                    System.out.println("Email updated.");
                }
                case "3" -> {
                    System.out.print("New password: ");
                    execUpdate("UPDATE users SET password_hash=? WHERE id=?",
                            PasswordUtil.hashPassword(sc.nextLine()), userId);
                    System.out.println("Password updated.");
                }
                default -> {}
            }
        } catch (SQLException e) {
            System.out.println("Update profile error: " + e.getMessage());
        }
    }

    public static void listZones() {
        String sql = "SELECT id,zone_name,active FROM parking_zones ORDER BY id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ParkingZone zone = new ParkingZone(
                        rs.getInt("id"),
                        rs.getString("zone_name"),
                        rs.getInt("active") == 1
                );
                System.out.println(zone.getId() + ". " + zone.getZoneName() + " | active=" + zone.isActive());
            }
        } catch (SQLException e) {
            System.out.println("List zones error: " + e.getMessage());
        }
    }

    public static void listSpots(int zoneId) {
        String sql = "SELECT id,spot_code,status,spot_type FROM parking_spots WHERE zone_id=? ORDER BY id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getInt("id") + " | " + rs.getString("spot_code")
                            + " | " + rs.getString("status") + " | Type: " + rs.getString("spot_type"));
                }
            }
        } catch (SQLException e) {
            System.out.println("List spots error: " + e.getMessage());
        }
    }

    public static void countPrint(String label, String sql) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int count = rs.next() ? rs.getInt("c") : 0;
            System.out.println(label + ": " + count);
        } catch (SQLException e) {
            System.out.println(label + ": error");
        }
    }

    public static void execUpdate(String sql, String value, int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }
    public static void listViolationRules() {
    String sql = "SELECT violation_type, fine_amount FROM violation_rules ORDER BY violation_type";
    try (Connection conn = DBConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        System.out.println("Violation rules:");
        boolean has = false;
        while (rs.next()) {
            has = true;
            System.out.println("- " + rs.getString("violation_type") + " = " + rs.getDouble("fine_amount"));
        }
        if (!has) {
            System.out.println("- none");
        }

    } catch (SQLException e) {
        System.out.println("List rules error: " + e.getMessage());
    }
}

    public static int readInt(Scanner sc, String prompt, boolean allowZero) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            try {
                int value = Integer.parseInt(input);
                if (!allowZero && value <= 0) {
                    System.out.println("Enter valid number (>0).");
                    continue;
                }
                return value;
            } catch (Exception e) {
                System.out.println("Invalid number.");
            }
        }
    }
}
