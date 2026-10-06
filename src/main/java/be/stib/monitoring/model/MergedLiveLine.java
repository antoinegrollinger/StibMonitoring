package be.stib.monitoring.model;

import java.util.List;

/**
 * A line with its directions merged into one list of stops: the platforms serving the same stop
 * in each direction are grouped together.
 *
 * @param directions the line's directions as returned by the per-direction live view
 * @param stops      the stops in the order of the first direction, each with its platforms
 */
public record MergedLiveLine(
        String lineId,
        List<LiveLineStops> directions,
        List<Stop> stops) {

    /**
     * @param id        id of the stop's first platform, usable as a key
     * @param name      name of the first platform; null if STIB has no details for it
     * @param platforms one per direction serving the stop, in direction order
     */
    public record Stop(
            String id,
            LocalizedName name,
            Double latitude,
            Double longitude,
            List<Platform> platforms) {
    }

    public record Platform(String direction, String stopId, boolean vehiclePresent) {
    }
}
