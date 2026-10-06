package be.stib.monitoring.model;

import java.util.List;

/** Stops of a line in one direction, with their details and whether a vehicle is currently there. */
public record LiveLineStops(
        String lineId,
        String direction,
        LocalizedName destination,
        List<Stop> stops) {

    public record Stop(
            String id,
            int order,
            LocalizedName name,
            Double latitude,
            Double longitude,
            boolean vehiclePresent) {
    }
}
