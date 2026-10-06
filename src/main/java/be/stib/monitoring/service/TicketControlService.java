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
    private final Clock clock;
    private final List<TicketControl> controls = new ArrayList<>();

    public TicketControlService(ControlProperties properties, StibClient stibClient, Clock clock) {
        this.properties = properties;
        this.stibClient = stibClient;
        this.clock = clock;
    }

    /**
     * Records a report. A blank line id or message is stored as null. The stop's details are looked
     * up in STIB's (cached) stop dataset; if that dataset cannot be loaded the report is still kept,
     * without them.
     *
     * @throws IllegalArgumentException when the stop is unknown, the type is missing, the line id is
     *                                  malformed or the message is too long
     */
    public TicketControl report(String stopId, String lineId, ControlType type, String message) {
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
        StopDetails stop = stopDetails(stopId);
        synchronized (this) {
            Instant now = clock.instant();
            TicketControl control = new TicketControl(
                    UUID.randomUUID().toString(), stopId, stop, line, type, text, now, now.plus(properties.ttl()));
            purgeExpired(now);
            controls.add(control);
            return control;
        }
    }

    /** Called outside the lock: the first call may fetch the dataset from STIB. */
    private StopDetails stopDetails(String stopId) {
        Map<String, StopDetails> details;
        try {
            details = stibClient.getAllStopDetails();
        } catch (StibApiException e) {
            return null;
        }
        StopDetails stop = details.get(stopId);
        if (stop == null) {
            throw new IllegalArgumentException("unknown stop " + stopId);
        }
        return stop;
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
