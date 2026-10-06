package be.stib.monitoring.service;

import be.stib.monitoring.model.LineStopDetails;
import be.stib.monitoring.model.LocalizedName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectionMergerTest {

    // Stops along a meridian, ~1000 m apart; a stop's opposite platform is ~20 m away.

    @Test
    void groupsOppositePlatformsOfTheSameStop() {
        List<List<String>> merged = merge(
                direction("V", stop("A1", "A", 0), stop("B1", "B", 1), stop("C1", "C", 2)),
                direction("F", stop("C2", "C", 2), stop("B2", "B", 1), stop("A2", "A", 0)));

        assertThat(merged).containsExactly(List.of("A1", "A2"), List.of("B1", "B2"), List.of("C1", "C2"));
    }

    @Test
    void keepsStopsServedInOneDirectionInBetween() {
        // The way back skips B and serves X instead.
        List<List<String>> merged = merge(
                direction("V", stop("A1", "A", 0), stop("B1", "B", 1), stop("C1", "C", 2), stop("D1", "D", 3)),
                direction("F", stop("D2", "D", 3), stop("X2", "X", 1.5), stop("A2", "A", 0)));

        assertThat(merged).containsExactly(
                List.of("A1", "A2"), List.of("X2"), List.of("B1"), List.of("C1"), List.of("D1", "D2"));
    }

    @Test
    void doesNotGroupDistantStopsWithTheSameName() {
        List<List<String>> merged = merge(
                direction("V", stop("A1", "Gare", 0), stop("B1", "B", 1)),
                direction("F", stop("B2", "B", 1), stop("A2", "Gare", 5)));

        assertThat(merged).containsExactly(List.of("A2"), List.of("A1"), List.of("B1", "B2"));
    }

    @Test
    void sharesPlatformsServedByBothDirections() {
        // A terminus loop: both directions use the same platform T.
        List<List<String>> merged = merge(
                direction("V", stop("A1", "A", 0), stop("T", "T", 1)),
                direction("F", stop("T", "T", 1), stop("A2", "A", 0)));

        assertThat(merged).containsExactly(List.of("A1", "A2"), List.of("T", "T"));
    }

    private static List<List<String>> merge(LineStopDetails... directions) {
        return DirectionMerger.merge(List.of(directions)).stream()
                .map(group -> group.stream().map(p -> p.stop().id()).toList())
                .toList();
    }

    private static LineStopDetails direction(String direction, LineStopDetails.Stop... stops) {
        return new LineStopDetails("7", direction, null, List.of(stops));
    }

    /** {@code km} along the line; the second platform of a stop is offset ~20 m to the east. */
    private static LineStopDetails.Stop stop(String id, String name, double km) {
        double longitude = id.endsWith("2") ? 4.3503 : 4.35;
        return new LineStopDetails.Stop(id, 0, new LocalizedName(name, name), 50.8 + km * 0.009, longitude);
    }
}
