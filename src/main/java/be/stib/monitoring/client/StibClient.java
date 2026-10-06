package be.stib.monitoring.client;

import be.stib.monitoring.config.CacheConfig;
import be.stib.monitoring.model.LineStops;
import be.stib.monitoring.model.LocalizedText;
import be.stib.monitoring.model.LocalizedName;
import be.stib.monitoring.model.StopDetails;
import be.stib.monitoring.model.StopDetailsResponse;
import be.stib.monitoring.model.StopsByLineResponse;
import be.stib.monitoring.model.TravellerMessage;
import be.stib.monitoring.model.TravellersInformationResponse;
import be.stib.monitoring.model.VehiclePosition;
import be.stib.monitoring.model.VehiclePositionsResponse;
import be.stib.monitoring.model.WaitingTime;
import be.stib.monitoring.model.WaitingTimesResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class StibClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public StibClient(RestClient stibRestClient, ObjectMapper objectMapper) {
        this.restClient = stibRestClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Calls {@code GET /static/stopsByLine/} for the whole network (one record per line and
     * direction) and groups the routes by line id. Static data, so cached.
     */
    @Cacheable(CacheConfig.STOPS_BY_LINE)
    public Map<String, List<LineStops>> getAllStopsByLine() {
        StopsByLineResponse response = get("/static/stopsByLine/", null,
                StopsByLineResponse.class, "stops by line");
        if (response == null || response.results() == null) {
            return Map.of();
        }
        return response.results().stream()
                .map(this::toLineStops)
                .collect(Collectors.groupingBy(LineStops::lineId, Collectors.toUnmodifiableList()));
    }

    /** Calls {@code GET /static/StopDetails/} for every stop, keyed by stop id. Static data, so cached. */
    @Cacheable(CacheConfig.STOP_DETAILS)
    public Map<String, StopDetails> getAllStopDetails() {
        StopDetailsResponse response = get("/static/StopDetails/", null,
                StopDetailsResponse.class, "stop details");
        if (response == null || response.results() == null) {
            return Map.of();
        }
        return response.results().stream()
                .map(this::toStopDetails)
                .collect(Collectors.toUnmodifiableMap(StopDetails::id, Function.identity(), (a, b) -> a));
    }

    /**
     * Calls {@code GET /rt/VehiclePositions/} for the whole network (a single small response)
     * and groups the vehicles by line id. Cached briefly ({@code stib.cache.live-ttl}); concurrent
     * callers wait for a single STIB call.
     */
    @Cacheable(cacheNames = CacheConfig.VEHICLE_POSITIONS, sync = true)
    public Map<String, List<VehiclePosition>> getAllVehiclePositions() {
        VehiclePositionsResponse response = get("/rt/VehiclePositions/", null,
                VehiclePositionsResponse.class, "vehicle positions");
        if (response == null || response.results() == null) {
            return Map.of();
        }
        return response.results().stream()
                .collect(Collectors.toMap(
                        VehiclePositionsResponse.Record::lineid,
                        record -> decode(record.vehiclepositions()).valueStream()
                                .map(v -> new VehiclePosition(
                                        v.path("directionId").asText(),
                                        v.path("pointId").asText(),
                                        v.path("distanceFromPoint").asInt()))
                                .toList(),
                        (a, b) -> Stream.concat(a.stream(), b.stream()).toList()));
    }

    /**
     * Calls {@code GET /rt/WaitingTimes/?where=pointid=<stopId>}; returns all lines, soonest first.
     * Cached briefly per stop ({@code stib.cache.live-ttl}); concurrent callers wait for a single STIB call.
     */
    @Cacheable(cacheNames = CacheConfig.WAITING_TIMES, sync = true)
    public List<WaitingTime> getWaitingTimes(String stopId) {
        WaitingTimesResponse response = get("/rt/WaitingTimes/", "pointid=" + stopId,
                WaitingTimesResponse.class, "waiting times for stop " + stopId);
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .flatMap(record -> decode(record.passingtimes()).valueStream()
                        .map(p -> toWaitingTime(p, record.lineid())))
                .filter(w -> w.expectedArrivalTime() != null)
                .sorted(Comparator.comparing(WaitingTime::expectedArrivalTime))
                .toList();
    }

    /**
     * Calls {@code GET /rt/TravellersInformation/}, which returns every current message for the
     * whole network in one response. Cached so it is fetched once per TTL rather than per request.
     */
    @Cacheable(CacheConfig.TRAVELLERS_INFORMATION)
    public List<TravellerMessage> getTravellersInformation() {
        TravellersInformationResponse response = get("/rt/TravellersInformation/", null,
                TravellersInformationResponse.class, "traveller information");
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream().map(this::toTravellerMessage).toList();
    }

    private <T> T get(String path, String where, Class<T> type, String description) {
        try {
            return restClient.get()
                    .uri(uri -> where == null
                            ? uri.path(path).build()
                            : uri.path(path).queryParam("where", "{where}").build(where))
                    .retrieve()
                    .body(type);
        } catch (RestClientException e) {
            throw new StibApiException("Failed to fetch " + description, e);
        }
    }

    private LineStops toLineStops(StopsByLineResponse.Record record) {
        List<LineStops.Stop> stops = decode(record.points()).valueStream()
                .map(p -> new LineStops.Stop(p.path("id").asText(), p.path("order").asInt()))
                .sorted(Comparator.comparingInt(LineStops.Stop::order))
                .toList();
        return new LineStops(record.lineid(), record.direction(), toName(record.destination()), stops);
    }

    private StopDetails toStopDetails(StopDetailsResponse.Record record) {
        JsonNode gps = decode(record.gpscoordinates());
        return new StopDetails(
                record.id(),
                toName(record.name()),
                gps.hasNonNull("latitude") ? gps.get("latitude").asDouble() : null,
                gps.hasNonNull("longitude") ? gps.get("longitude").asDouble() : null);
    }

    private WaitingTime toWaitingTime(JsonNode passing, String recordLineId) {
        String time = passing.path("expectedArrivalTime").asText(null);
        JsonNode message = passing.path("message");
        return new WaitingTime(
                passing.path("lineId").asText(recordLineId),
                toName(passing.get("destination")),
                time == null ? null : OffsetDateTime.parse(time),
                message.isMissingNode() ? null : toText(message));
    }

    private TravellerMessage toTravellerMessage(TravellersInformationResponse.Record record) {
        // content is a list of blocks, each with a list of translations; keep the first text found.
        JsonNode text = decode(record.content()).valueStream()
                .flatMap(block -> block.path("text").valueStream())
                .findFirst()
                .orElse(objectMapper.missingNode());
        return new TravellerMessage(
                record.id(),
                record.priority() == null ? Integer.MAX_VALUE : record.priority(),
                toText(text),
                ids(record.lines()),
                ids(record.points()));
    }

    private static LocalizedText toText(JsonNode node) {
        return new LocalizedText(node.path("en").asText(null), node.path("fr").asText(null), node.path("nl").asText(null));
    }

    private List<String> ids(JsonNode node) {
        return decode(node).valueStream().map(item -> item.path("id").asText()).toList();
    }

    private LocalizedName toName(JsonNode node) {
        JsonNode name = decode(node);
        return new LocalizedName(name.path("fr").asText(null), name.path("nl").asText(null));
    }

    /** Fields may arrive either as nested JSON or as a JSON-encoded string. */
    private JsonNode decode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return objectMapper.missingNode();
        }
        if (!node.isTextual()) {
            return node;
        }
        try {
            return objectMapper.readTree(node.asText());
        } catch (JsonProcessingException e) {
            throw new StibApiException("Unexpected payload from STIB API: " + node.asText(), e);
        }
    }
}
