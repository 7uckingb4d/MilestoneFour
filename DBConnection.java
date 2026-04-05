import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DBConnection {
    private static final String DB_URL = "jdbc:sqlite:smart_parking.db";

    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("PRAGMA journal_mode = WAL;");
            st.execute("PRAGMA synchronous = NORMAL;");
            st.execute("PRAGMA busy_timeout = 10000;");
        }
        return conn;
    }

    public static void init() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {

            // NO HARD RESET: keep existing data
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  email TEXT NOT NULL UNIQUE,
                  password_hash TEXT NOT NULL,
                  role TEXT NOT NULL CHECK(role IN ('ADMIN','ATTENDANT','CUSTOMER')),
                  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','INACTIVE'))
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS parking_rates (
                  parking_type TEXT PRIMARY KEY CHECK(parking_type IN ('NORMAL','EV','PWD_SENIOR','VIP')),
                  hourly_rate REAL NOT NULL CHECK(hourly_rate >= 0)
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS parking_zones (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  zone_name TEXT NOT NULL UNIQUE,
                  active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1))
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS parking_spots (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  zone_id INTEGER NOT NULL,
                  spot_code TEXT NOT NULL,
                  spot_type TEXT NOT NULL CHECK(spot_type IN ('NORMAL','EV','PWD_SENIOR','VIP')),
                  status TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK(status IN ('AVAILABLE','RESERVED','OCCUPIED','MAINTENANCE')),
                  UNIQUE(zone_id, spot_code),
                  FOREIGN KEY(zone_id) REFERENCES parking_zones(id)
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS vehicles (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  customer_id INTEGER,
                  plate_number TEXT NOT NULL UNIQUE,
                  make TEXT, model TEXT, color TEXT,
                  is_guest INTEGER NOT NULL DEFAULT 0 CHECK(is_guest IN (0,1)),
                  FOREIGN KEY(customer_id) REFERENCES users(id)
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS reservations (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  customer_id INTEGER,
                  vehicle_id INTEGER NOT NULL,
                  zone_id INTEGER NOT NULL,
                  spot_id INTEGER NOT NULL,
                  start_time TEXT NOT NULL,
                  end_time TEXT NOT NULL,
                  check_in_time TEXT,
                  check_out_time TEXT,
                  status TEXT NOT NULL CHECK(status IN ('PENDING','CONFIRMED','CHECKED_IN','COMPLETED','DENIED','EXPIRED','CANCELLED')),
                  payment_mode TEXT NOT NULL CHECK(payment_mode IN ('PAY_NOW','CHECKOUT_PAY')),
                  reservation_fee REAL NOT NULL DEFAULT 0,
                  hourly_rate_snapshot REAL NOT NULL DEFAULT 0,
                  parking_fee REAL NOT NULL DEFAULT 0,
                  overstay_fee REAL NOT NULL DEFAULT 0,
                  total_amount REAL NOT NULL DEFAULT 0,
                  billing_status TEXT NOT NULL DEFAULT 'UNPAID' CHECK(billing_status IN ('UNPAID','PAID')),
                  created_by_attendant INTEGER NOT NULL DEFAULT 0 CHECK(created_by_attendant IN (0,1)),
                  created_at TEXT NOT NULL DEFAULT (datetime('now')),
                  FOREIGN KEY(customer_id) REFERENCES users(id),
                  FOREIGN KEY(vehicle_id) REFERENCES vehicles(id),
                  FOREIGN KEY(zone_id) REFERENCES parking_zones(id),
                  FOREIGN KEY(spot_id) REFERENCES parking_spots(id)
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS tickets (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  reservation_id INTEGER,
                  customer_id INTEGER,
                  plate_number TEXT NOT NULL,
                  violation_type TEXT NOT NULL CHECK(violation_type IN ('BLOCKING','NO_PARKING')),
                  fine_amount REAL NOT NULL CHECK(fine_amount >= 0),
                  status TEXT NOT NULL DEFAULT 'UNPAID' CHECK(status IN ('UNPAID','PAID','VOID')),
                  issued_at TEXT NOT NULL DEFAULT (datetime('now')),
                  FOREIGN KEY(reservation_id) REFERENCES reservations(id),
                  FOREIGN KEY(customer_id) REFERENCES users(id)
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS payments (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  reservation_id INTEGER,
                  ticket_id INTEGER,
                  customer_id INTEGER,
                  amount REAL NOT NULL CHECK(amount >= 0),
                  payment_method TEXT NOT NULL DEFAULT 'CASH',
                  paid_at TEXT NOT NULL DEFAULT (datetime('now')),
                  FOREIGN KEY(reservation_id) REFERENCES reservations(id),
                  FOREIGN KEY(ticket_id) REFERENCES tickets(id),
                  FOREIGN KEY(customer_id) REFERENCES users(id)
                );
            """);

            // seed users (won't duplicate)
            st.execute("INSERT OR IGNORE INTO users(id,name,email,password_hash,role,status) VALUES(1,'Admin','admin@spts.com','" + PasswordUtil.hashPassword("admin123") + "','ADMIN','ACTIVE')");
            st.execute("INSERT OR IGNORE INTO users(id,name,email,password_hash,role,status) VALUES(2,'Attendant','attendant@spts.com','" + PasswordUtil.hashPassword("att123") + "','ATTENDANT','ACTIVE')");
            st.execute("INSERT OR IGNORE INTO users(id,name,email,password_hash,role,status) VALUES(3,'Test1','test@spts.com','" + PasswordUtil.hashPassword("test") + "','CUSTOMER','ACTIVE')");

            // seed rates (upsert to enforce latest rates)
            // seed rates (upsert to enforce latest rates)
            st.execute("INSERT INTO parking_rates(parking_type,hourly_rate) VALUES('NORMAL',20) ON CONFLICT(parking_type) DO UPDATE SET hourly_rate=excluded.hourly_rate");
            st.execute("INSERT INTO parking_rates(parking_type,hourly_rate) VALUES('EV',40) ON CONFLICT(parking_type) DO UPDATE SET hourly_rate=excluded.hourly_rate");
            st.execute("INSERT INTO parking_rates(parking_type,hourly_rate) VALUES('PWD_SENIOR',16) ON CONFLICT(parking_type) DO UPDATE SET hourly_rate=excluded.hourly_rate");
            st.execute("INSERT INTO parking_rates(parking_type,hourly_rate) VALUES('VIP',50) ON CONFLICT(parking_type) DO UPDATE SET hourly_rate=excluded.hourly_rate");

            // seed zones
            st.execute("INSERT OR IGNORE INTO parking_zones(id,zone_name,active) VALUES(1,'Zone A',1)");
            st.execute("INSERT OR IGNORE INTO parking_zones(id,zone_name,active) VALUES(2,'Zone B',1)");
            st.execute("INSERT OR IGNORE INTO parking_zones(id,zone_name,active) VALUES(3,'Zone C',1)");

            // seed spots
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(1,'A-01','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(1,'A-02','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(1,'A-03','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(1,'A-04','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(1,'A-05','EV','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(1,'A-06','EV','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(1,'A-07','PWD_SENIOR','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(1,'A-08','PWD_SENIOR','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(1,'A-09','VIP','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(1,'A-10','VIP','AVAILABLE')");

            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(2,'B-01','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(2,'B-02','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(2,'B-03','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(2,'B-04','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(2,'B-05','EV','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(2,'B-06','EV','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(2,'B-07','PWD_SENIOR','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(2,'B-08','PWD_SENIOR','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(2,'B-09','VIP','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(2,'B-10','VIP','AVAILABLE')");
   
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(3,'C-01','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(3,'C-02','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(3,'C-03','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(3,'C-04','NORMAL','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(3,'C-05','EV','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(3,'C-06','EV','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(3,'C-07','PWD_SENIOR','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(3,'C-08','PWD_SENIOR','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(3,'C-09','VIP','AVAILABLE')");
            st.execute("INSERT OR IGNORE INTO parking_spots(zone_id,spot_code,spot_type,status) VALUES(3,'C-10','VIP','AVAILABLE')");
            System.out.println("Database ready.");
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }
}
