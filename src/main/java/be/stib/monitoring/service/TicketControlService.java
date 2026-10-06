package be.stib.monitoring.service;

import be.stib.monitoring.client.StibApiException;
import be.stib.monitoring.client.StibClient;
import be.stib.monitoring.config.ControlProperties;
import be.stib.monitoring.model.ControlType;
import be.stib.monitoring.model.StopDetails;
import be.stib.monitoring.model.TicketControl;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ticket controls reported by travellers. Reports are kept in memory only (they are short-lived
 * anyway) and dropped once they expire, so they are lost when the backend restarts.
 */
@Service
public class TicketControlService {

    private static final Pattern LINE_ID = Pattern.compile("[A-Za-z0-9]+");

    private final ControlProperties properties;
    private final StibClient stibClient;
    private final LineStopsService lineStops;
    private final Clock clock;
    private final List<TicketControl> controls = new ArrayList<>();

    public TicketControlService(ControlProperties properties, StibClient stibClient, LineStopsService lineStops,
                                Clock clock) {
        this.properties = properties;
        this.stibClient = stibClient;
        this.lineStops = lineStops;
        this.clock = clock;
    }

    /**
     * Records a report. A blank line id or message is stored as null. The stop's details are looked
     * up in STIB's (cached) stop dataset; if that dataset cannot be loaded the report is still kept,
     * without them.
     *
     * @param bothDirections also report the control at the platforms serving the same stop in the
     *                       other direction(s) of the line (or, without a line, of the first line
     *                       serving the stop)
     * @return one report per platform, the requested one first
     * @throws IllegalArgumentException when the stop is unknown, the type is missing, the line id is
     *                                  malformed or the message is too long
     */
    public List<TicketControl> report(String stopId, String lineId, ControlType type, String message,
                                      boolean bothDirections) {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        String line = lineId == null || lineId.isBlank() ? null : lineId.strip().toUpperCase();
        if (line != null && !LINE_ID.matcher(line).matches()) {
            throw new IllegalArgumentException("lineId must be alphanumeric");
        }
        String text = message == null || message.isBlank() ? null : message.strip();
        if (text != null && text.length() > properties.maxMessageLength()) {
            throw new IllegalArgumentException("message must be at most " + properties.maxMessageLength() + " characters");
        }
        Map<String, StopDetails> details = stopDetails();
        if (details != null && !details.containsKey(stopId)) {
            throw new IllegalArgumentException("unknown stop " + stopId);
        }
        List<String> stopIds = new ArrayList<>(List.of(stopId));
        if (bothDirections) {
            platforms(line, stopId).stream().filter(id -> !id.equals(stopId)).forEach(stopIds::add);
        }
        synchronized (this) {
            Instant now = clock.instant();
            List<TicketControl> reported = stopIds.stream()
                    .map(id -> new TicketControl(UUID.randomUUID().toString(), id,
                            details == null ? null : details.get(id), line, type, text,
                            now, now.plus(properties.ttl())))
                    .toList();
            purgeExpired(now);
            controls.addAll(reported);
            return reported;
        }
    }

    /** Platforms of the same stop; just the stop itself if STIB's line data cannot be loaded. */
    private List<String> platforms(String lineId, String stopId) {
        try {
            return lineStops.getSameStopPlatforms(lineId, stopId);
        } catch (StibApiException e) {
            return List.of(stopId);
        }
    }

    /** STIB's stop dataset, or null if it cannot be loaded. Called outside the lock: the first call may fetch it. */
    private Map<String, StopDetails> stopDetails() {
        try {
            return stibClient.getAllStopDetails();
        } catch (StibApiException e) {
            return null;
        }
    }

    /** Reports that have not expired yet, most recent first. */
    public synchronized List<TicketControl> active() {
        purgeExpired(clock.instant());
        return controls.stream()
                .sorted(Comparator.comparing(TicketControl::reportedAt).reversed())
                .toList();
    }

    private void purgeExpired(Instant now) {
        controls.removeIf(c -> !c.expiresAt().isAfter(now));
    }
}
