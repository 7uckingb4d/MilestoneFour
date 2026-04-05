package smartparking;

public class ParkingZone {
    private final int id;
    private final String zoneName;
    private final boolean active;

    public ParkingZone(int id, String zoneName, boolean active) {
        this.id = id;
        this.zoneName = zoneName;
        this.active = active;
    }

    public int getId() { return id; }
    public String getZoneName() { return zoneName; }
    public boolean isActive() { return active; }
}
