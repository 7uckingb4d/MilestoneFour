package smartparking;

public class User {
    private final int id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final String role;
    private final String status;

    private User(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.email = builder.email;
        this.passwordHash = builder.passwordHash;
        this.role = builder.role;
        this.status = builder.status;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public String getStatus() { return status; }

    public static class Builder {
        private int id;
        private String name;
        private String email;
        private String passwordHash;
        private String role = "CUSTOMER";
        private String status = "ACTIVE";

        public Builder id(int id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public Builder role(String role) { this.role = role; return this; }
        public Builder status(String status) { this.status = status; return this; }

        public User build() { return new User(this); }
    }
}