public class Vehicle {
    private final int id;
    private final int customerId;
    private final String plateNumber;
    private final String make;
    private final String model;
    private final String color;

    public Vehicle(int id, int customerId, String plateNumber, String make, String model, String color) {
        this.id = id;
        this.customerId = customerId;
        this.plateNumber = plateNumber;
        this.make = make;
        this.model = model;
        this.color = color;
    }

    public int getId() { return id; }
    public int getCustomerId() { return customerId; }
    public String getPlateNumber() { return plateNumber; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public String getColor() { return color; }
}
