package be.stib.monitoring.service;

import be.stib.monitoring.client.StibClient;
import be.stib.monitoring.model.LineMessage;
import be.stib.monitoring.model.LineStops;
import be.stib.monitoring.model.LiveLineStops;
import be.stib.monitoring.model.MergedLiveLine;
import be.stib.monitoring.model.LocalizedName;
import be.stib.monitoring.model.LocalizedText;
import be.stib.monitoring.model.StopDetails;
import be.stib.monitoring.model.TravellerMessage;
import be.stib.monitoring.model.VehiclePosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LineStopsServiceTest {

    private final StibClient client = mock(StibClient.class);
    private final LineStopsService service = new LineStopsService(client);

    // Line 1: A -> B -> C -> D along a meridian; 0.009° of latitude is ~1000 m.
    // Line 5: B -> E, sharing platform B with line 1.
    @BeforeEach
    void givenTwoLinesSharingAStop() {
        when(client.getAllStopsByLine()).thenReturn(Map.of(
                "1", List.of(new LineStops("1", "City", new LocalizedName("D", "D"),
                        List.of(new LineStops.Stop("A", 1), new LineStops.Stop("B", 2),
                                new LineStops.Stop("C", 3), new LineStops.Stop("D", 4)))),
                "5", List.of(new LineStops("5", "Suburb", new LocalizedName("E", "E"),
                        List.of(new LineStops.Stop("B", 1), new LineStops.Stop("E", 2)))),
                "T39", List.of(),
                "12", List.of()));
        when(client.getAllStopDetails()).thenReturn(Map.of(
                "A", stop("A", 50.800), "B", stop("B", 50.809),
                "C", stop("C", 50.818), "D", stop("D", 50.827), "E", stop("E", 50.836)));
    }

    @Test
    void listsLinesNumericallyThenAlphabetically() {
        assertThat(service.getLineIds()).containsExactly("1", "5", "12", "T39");
    }

    @Test
    void assignsEachVehicleToTheClosestOfItsLastAndNextStop() {
        when(client.getAllVehiclePositions()).thenReturn(Map.of("1", List.of(
                new VehiclePosition("D", "A", 0),     // at A
                new VehiclePosition("D", "B", 300),   // closer to B than C
                new VehiclePosition("D", "C", 700)))); // closer to D than C

        assertThat(presence("1")).containsExactly(true, true, false, true);
    }

    @Test
    void keepsVehiclePastTheLastStopAtThatStopAndIgnoresUnknownStops() {
        when(client.getAllVehiclePositions()).thenReturn(Map.of("1", List.of(
                new VehiclePosition("D", "D", 500),
                new VehiclePosition("D", "UNKNOWN", 0))));

        assertThat(presence("1")).containsExactly(false, false, false, true);
    }

    @Test
    void matchesVehiclesLineByLineWhenSeveralLinesAreRequested() {
        // A line 5 vehicle at shared platform B must not show up on line 1.
        when(client.getAllVehiclePositions()).thenReturn(Map.of(
                "5", List.of(new VehiclePosition("E", "B", 0)),
                "92", List.of(new VehiclePosition("X", "A", 0))));

        List<LiveLineStops> lines = service.getLiveStopsByLines(List.of("5", "1", "unknown"));

        assertThat(lines).extracting(LiveLineStops::lineId).containsExactly("5", "1");
        assertThat(lines.get(0).stops()).extracting(LiveLineStops.Stop::vehiclePresent).containsExactly(true, false);
        assertThat(lines.get(1).stops()).extracting(LiveLineStops.Stop::vehiclePresent).containsOnly(false);
    }

    @Test
    void keepsMessagesForTheLineAndStopOnlyMessagesForItsStops() {
        LocalizedText text = new LocalizedText("en", "fr", "nl");
        when(client.getTravellersInformation()).thenReturn(List.of(
                new TravellerMessage("line", 5, text, List.of("1"), List.of("B", "X")),
                new TravellerMessage("stop", 4, text, List.of(), List.of("C")),
                new TravellerMessage("otherLineSharedStop", 4, text, List.of("5"), List.of("A")),
                new TravellerMessage("elsewhere", 4, text, List.of(), List.of("X")),
                new TravellerMessage("strike", 7, text, List.of("1"), List.of("A", "B", "C", "D"))));

        List<LineMessage> messages = service.getLineMessages(List.of("1"));

        assertThat(messages).extracting(LineMessage::id).containsExactly("stop", "line", "strike");
        assertThat(messages).extracting(LineMessage::affectedStopIds)
                .containsExactly(List.of("C"), List.of("B"), List.of());
    }

    @Test
    void listsAMessageOnceWithEveryRequestedLineItConcerns() {
        LocalizedText text = new LocalizedText("en", "fr", "nl");
        when(client.getTravellersInformation()).thenReturn(List.of(
                new TravellerMessage("sharedStop", 4, text, List.of(), List.of("B")),
                new TravellerMessage("both", 5, text, List.of("1", "5"), List.of("A", "E"))));

        List<LineMessage> messages = service.getLineMessages(List.of("1", "5"));

        assertThat(messages).extracting(LineMessage::id).containsExactly("sharedStop", "both");
        assertThat(messages.get(0).lineIds()).containsExactly("1", "5");
        assertThat(messages.get(0).affectedStopIds()).containsExactly("B");
        assertThat(messages.get(1).affectedStopIds()).containsExactly("A", "E");
    }

    @Test
    void mergesTheDirectionsOfALineWithTheVehiclesOfBoth() {
        givenLine7WithTwoDirections();
        when(client.getAllVehiclePositions()).thenReturn(Map.of("7", List.of(
                new VehiclePosition("C", "A7", 0),     // going to C, at A
                new VehiclePosition("A", "C8", 0))));  // coming back, at C

        MergedLiveLine line = service.getMergedLiveStopsByLines(List.of("7")).getFirst();

        assertThat(line.directions()).hasSize(2);
        assertThat(line.stops()).extracting(MergedLiveLine.Stop::id).containsExactly("A7", "B7", "C7");
        assertThat(line.stops().getFirst().platforms()).containsExactly(
                new MergedLiveLine.Platform("V", "A7", true), new MergedLiveLine.Platform("F", "A8", false));
        assertThat(line.stops().getLast().platforms()).containsExactly(
                new MergedLiveLine.Platform("V", "C7", false), new MergedLiveLine.Platform("F", "C8", true));
    }

    @Test
    void findsThePlatformsOfTheSameStopInBothDirections() {
        givenLine7WithTwoDirections();

        assertThat(service.getSameStopPlatforms("7", "B8")).containsExactly("B7", "B8");
        assertThat(service.getSameStopPlatforms(null, "B8")).containsExactly("B7", "B8");
        assertThat(service.getSameStopPlatforms(null, "NOWHERE")).containsExactly("NOWHERE");
    }

    /** Line 7: A -> B -> C (platforms A7, B7, C7) and back (C8, B8, A8), ~20 m from the first ones. */
    private void givenLine7WithTwoDirections() {
        when(client.getAllStopsByLine()).thenReturn(Map.of("7", List.of(
                new LineStops("7", "V", new LocalizedName("C", "C"),
                        List.of(new LineStops.Stop("A7", 1), new LineStops.Stop("B7", 2), new LineStops.Stop("C7", 3))),
                new LineStops("7", "F", new LocalizedName("A", "A"),
                        List.of(new LineStops.Stop("C8", 1), new LineStops.Stop("B8", 2), new LineStops.Stop("A8", 3))))));
        when(client.getAllStopDetails()).thenReturn(Map.of(
                "A7", named("A7", "A", 50.800, 4.35), "B7", named("B7", "B", 50.809, 4.35), "C7", named("C7", "C", 50.818, 4.35),
                "A8", named("A8", "A", 50.800, 4.3503), "B8", named("B8", "B", 50.809, 4.3503), "C8", named("C8", "C", 50.818, 4.3503)));
    }

    private static StopDetails named(String id, String name, double latitude, double longitude) {
        return new StopDetails(id, new LocalizedName(name, name), latitude, longitude);
    }

    private List<Boolean> presence(String lineId) {
        return service.getLiveStopsByLines(List.of(lineId)).getFirst().stops().stream()
                .map(LiveLineStops.Stop::vehiclePresent)
                .toList();
    }

    private static StopDetails stop(String id, double latitude) {
        return new StopDetails(id, new LocalizedName(id, id), latitude, 4.35);
    }
}
