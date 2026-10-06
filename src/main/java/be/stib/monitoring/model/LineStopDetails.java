package be.stib.monitoring.model;

import java.util.List;

/** Stops of a line in one direction, enriched with their details. */
public record LineStopDetails(
        String lineId,
        String direction,
        LocalizedName destination,
        List<Stop> stops) {

    /** {@code name}, {@code latitude} and {@code longitude} are null if STIB has no details for the stop. */
    public record Stop(
            String id,
            int order,
            LocalizedName name,
            Double latitude,
            Double longitude) {
    }
}
