package be.stib.monitoring.service;

import be.stib.monitoring.model.LineStopDetails;
import be.stib.monitoring.model.LocalizedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups the platforms of a line's directions that serve the same stop. STIB gives each platform
 * its own stop id, but the platforms of a stop share its name and lie close together.
 * <p>
 * The first direction gives the order. Each further direction is walked in the matching sense
 * (reversed when it runs the other way) and each of its stops joins the first group after the
 * previous match that serves the same stop, falling back to any free group of the same stop;
 * stops served in only one direction (branches, loops) get a group of their own in between.
 */
final class DirectionMerger {

    /** Platforms with the same name further apart than this are different stops. */
    static final double SAME_NAME_MAX_METRES = 500;
    /** Platforms without a name are only grouped when this close. */
    static final double UNNAMED_MAX_METRES = 100;

    record Platform(String direction, LineStopDetails.Stop stop) {
    }

    private DirectionMerger() {
    }

    static List<List<Platform>> merge(List<LineStopDetails> directions) {
        List<List<Platform>> merged = new ArrayList<>();
        for (LineStopDetails direction : directions) {
            List<LineStopDetails.Stop> stops = direction.stops();
            if (!merged.isEmpty() && runsOpposite(merged, stops)) {
                stops = stops.reversed();
            }
            int last = -1;
            for (LineStopDetails.Stop stop : stops) {
                Platform platform = new Platform(direction.direction(), stop);
                int match = findGroup(merged, platform, last);
                if (match >= 0) {
                    merged.get(match).add(platform);
                    last = match;
                } else {
                    merged.add(last + 1, new ArrayList<>(List.of(platform)));
                    last++;
                }
            }
        }
        return merged;
    }

    private static int findGroup(List<List<Platform>> merged, Platform platform, int last) {
        int fallback = -1;
        for (int i = 0; i < merged.size(); i++) {
            List<Platform> group = merged.get(i);
            boolean free = group.stream().noneMatch(p -> p.direction().equals(platform.direction()));
            if (free && sameStop(group.getFirst().stop(), platform.stop())) {
                if (i > last) {
                    return i;
                }
                if (fallback < 0) {
                    fallback = i;
                }
            }
        }
        return fallback;
    }

    /** Whether a direction starts nearer the end of the merged list than its start. */
    private static boolean runsOpposite(List<List<Platform>> merged, List<LineStopDetails.Stop> stops) {
        if (stops.isEmpty()) {
            return false;
        }
        LineStopDetails.Stop first = stops.getFirst();
        LineStopDetails.Stop start = merged.getFirst().getFirst().stop();
        LineStopDetails.Stop end = merged.getLast().getFirst().stop();
        if (!hasCoordinates(first) || !hasCoordinates(start) || !hasCoordinates(end)) {
            return true; // the usual case: a line's second direction is the way back
        }
        return LineStopsService.distanceMetres(first, end) < LineStopsService.distanceMetres(first, start);
    }

    static boolean sameStop(LineStopDetails.Stop a, LineStopDetails.Stop b) {
        boolean located = hasCoordinates(a) && hasCoordinates(b);
        double distance = located ? LineStopsService.distanceMetres(a, b) : Double.NaN;
        if (sameName(a.name(), b.name())) {
            return !located || distance <= SAME_NAME_MAX_METRES;
        }
        return a.name() == null && b.name() == null && located && distance <= UNNAMED_MAX_METRES;
    }

    private static boolean sameName(LocalizedName a, LocalizedName b) {
        if (a == null || b == null) {
            return false;
        }
        return (a.fr() != null && a.fr().equalsIgnoreCase(b.fr()))
                || (a.nl() != null && a.nl().equalsIgnoreCase(b.nl()));
    }

    private static boolean hasCoordinates(LineStopDetails.Stop stop) {
        return stop.latitude() != null && stop.longitude() != null;
    }
}
