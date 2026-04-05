package smartparking;

public class ParkingSpot {
    private final int id;
    private final int zoneId;
    private final String spotCode;
    private final String status;

    public ParkingSpot(int id, int zoneId, String spotCode, String status) {
        this.id = id;
        this.zoneId = zoneId;
        this.spotCode = spotCode;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public int getZoneId() {
        return zoneId;
    }

    public String getSpotCode() {
        return spotCode;
    }

    public String getStatus() {
        return status;
    }
}
