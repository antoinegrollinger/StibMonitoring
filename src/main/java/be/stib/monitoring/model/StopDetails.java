package be.stib.monitoring.model;

public record StopDetails(
        String id,
        LocalizedName name,
        Double latitude,
        Double longitude) {
}
