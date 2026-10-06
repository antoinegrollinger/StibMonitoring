package be.stib.monitoring.service;

import be.stib.monitoring.client.StibClient;
import be.stib.monitoring.model.LineMessage;
import be.stib.monitoring.model.LineStopDetails;
import be.stib.monitoring.model.LineStops;
import be.stib.monitoring.model.LiveLineStops;
import be.stib.monitoring.model.StopDetails;
import be.stib.monitoring.model.TravellerMessage;
import be.stib.monitoring.model.VehiclePosition;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Line-level views built from whole-network STIB datasets: whatever the number of lines requested,
 * each request costs at most one call per dataset (and the static ones are cached).
 */
@Service
public class LineStopsService {

    private static final double EARTH_RADIUS_METRES = 6_371_000;

    /** Numeric lines first in numeric order, then the others (e.g. T39) alphabetically. */
    private static final Comparator<String> LINE_ORDER = Comparator
            .comparing((String id) -> !id.chars().allMatch(Character::isDigit))
            .thenComparingInt(String::length)
            .thenComparing(Comparator.naturalOrder());

    private final StibClient stibClient;

    public LineStopsService(StibClient stibClient) {
        this.stibClient = stibClient;
    }

    /** Ids of every line in the network. */
    public List<String> getLineIds() {
        return stibClient.getAllStopsByLine().keySet().stream().sorted(LINE_ORDER).toList();
    }

    public List<LineStops> getStopsByLine(String lineId) {
        return stibClient.getAllStopsByLine().getOrDefault(lineId, List.of());
    }

    /** Stops of the given lines with their details, in the order requested. Unknown lines are skipped. */
    public List<LineStopDetails> getStopDetailsByLines(Collection<String> lineIds) {
        Map<String, List<LineStops>> stopsByLine = stibClient.getAllStopsByLine();
        Map<String, StopDetails> details = stibClient.getAllStopDetails();
        return new LinkedHashSet<>(lineIds).stream()
                .flatMap(lineId -> stopsByLine.getOrDefault(lineId, List.of()).stream())
                .map(line -> enrich(line, details))
                .toList();
    }

    /**
     * Stop details for the given lines, flagging the stops a vehicle is currently closest to.
     * Stop ids are specific to a direction (one per platform), but a platform can be shared by
     * several lines, so vehicles are matched line by line.
     */
    public List<LiveLineStops> getLiveStopsByLines(Collection<String> lineIds) {
        List<LineStopDetails> lines = getStopDetailsByLines(lineIds);
        Map<String, List<VehiclePosition>> vehiclesByLine = stibClient.getAllVehiclePositions();
        Map<String, Set<String>> occupiedByLine = lines.stream()
                .collect(Collectors.groupingBy(LineStopDetails::lineId)).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e ->
                        occupiedStopIds(e.getValue(), vehiclesByLine.getOrDefault(e.getKey(), List.of()))));
        return lines.stream()
                .map(line -> {
                    Set<String> occupied = occupiedByLine.get(line.lineId());
                    return new LiveLineStops(line.lineId(), line.direction(), line.destination(),
                            line.stops().stream()
                                    .map(s -> new LiveLineStops.Stop(s.id(), s.order(), s.name(),
                                            s.latitude(), s.longitude(), occupied.contains(s.id())))
                                    .toList());
                })
                .toList();
    }

    /**
     * Traveller messages relevant to the given lines, each listed once with the lines it concerns.
     * A message concerns a line when it is tagged with it, or when it is tagged with no line at
     * all and mentions one of its stops; messages tagged with other lines are left out even when
     * they mention a shared stop. A message mentioning every stop of a line counts as line-wide
     * and flags none of that line's stops.
     */
    public List<LineMessage> getLineMessages(Collection<String> lineIds) {
        Map<String, Set<String>> stopIdsByLine = new HashMap<>();
        for (String lineId : new LinkedHashSet<>(lineIds)) {
            stopIdsByLine.put(lineId, getStopsByLine(lineId).stream()
                    .flatMap(line -> line.stops().stream())
                    .map(LineStops.Stop::id)
                    .collect(Collectors.toSet()));
        }
        List<String> requested = List.copyOf(new LinkedHashSet<>(lineIds));
        return stibClient.getTravellersInformation().stream()
                .sorted(Comparator.comparingInt(TravellerMessage::priority).thenComparing(TravellerMessage::id))
                .map(m -> {
                    List<String> concerned = requested.stream()
                            .filter(lineId -> m.lineIds().contains(lineId)
                                    || (m.lineIds().isEmpty()
                                        && m.stopIds().stream().anyMatch(stopIdsByLine.get(lineId)::contains)))
                            .toList();
                    List<String> affected = concerned.stream()
                            .flatMap(lineId -> affectedStops(m, stopIdsByLine.get(lineId)).stream())
                            .distinct()
                            .toList();
                    return new LineMessage(m.id(), m.priority(), m.text(), concerned, affected);
                })
                .filter(m -> !m.lineIds().isEmpty())
                .toList();
    }

    /** Stops of the line a message mentions; empty when it covers the whole line (e.g. a strike). */
    private static List<String> affectedStops(TravellerMessage message, Set<String> lineStopIds) {
        List<String> affected = message.stopIds().stream().filter(lineStopIds::contains).distinct().toList();
        return affected.size() == lineStopIds.size() ? List.of() : affected;
    }

    private static Set<String> occupiedStopIds(List<LineStopDetails> directions, List<VehiclePosition> vehicles) {
        Map<String, LineStopDetails.Stop> stopsById = new HashMap<>();
        Map<String, LineStopDetails.Stop> nextStopById = new HashMap<>();
        for (LineStopDetails direction : directions) {
            List<LineStopDetails.Stop> stops = direction.stops();
            for (int i = 0; i < stops.size(); i++) {
                stopsById.putIfAbsent(stops.get(i).id(), stops.get(i));
                if (i + 1 < stops.size()) {
                    nextStopById.putIfAbsent(stops.get(i).id(), stops.get(i + 1));
                }
            }
        }
        return vehicles.stream()
                .map(v -> closestStopId(v, stopsById.get(v.pointId()), nextStopById.get(v.pointId())))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * STIB only gives the last stop passed and the metres travelled since. The vehicle is assigned
     * to whichever of that stop and the next one it is closer to, using the straight-line distance
     * between the two stops as the length of the segment. Returns null when the stop is not on
     * the line (e.g. a terminus platform outside the published route).
     */
    private static String closestStopId(VehiclePosition vehicle, LineStopDetails.Stop last, LineStopDetails.Stop next) {
        if (last == null) {
            return null;
        }
        if (next == null || !hasCoordinates(last) || !hasCoordinates(next)) {
            return last.id();
        }
        double segment = distanceMetres(last, next);
        return vehicle.distanceFromPoint() <= segment / 2 ? last.id() : next.id();
    }

    private static boolean hasCoordinates(LineStopDetails.Stop stop) {
        return stop.latitude() != null && stop.longitude() != null;
    }

    /** Haversine distance between two stops. */
    static double distanceMetres(LineStopDetails.Stop a, LineStopDetails.Stop b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude()))
                * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_METRES * Math.asin(Math.sqrt(h));
    }

    private static LineStopDetails enrich(LineStops line, Map<String, StopDetails> details) {
        List<LineStopDetails.Stop> stops = line.stops().stream()
                .map(stop -> {
                    StopDetails d = details.get(stop.id());
                    return d == null
                            ? new LineStopDetails.Stop(stop.id(), stop.order(), null, null, null)
                            : new LineStopDetails.Stop(stop.id(), stop.order(), d.name(), d.latitude(), d.longitude());
                })
                .toList();
        return new LineStopDetails(line.lineId(), line.direction(), line.destination(), stops);
    }
}
