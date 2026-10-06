package be.stib.monitoring.model;

import java.util.List;

/** Ordered list of stops served by a line in one direction. */
public record LineStops(
        String lineId,
        String direction,
        LocalizedName destination,
        List<Stop> stops) {

    public record Stop(String id, int order) {
    }
}
