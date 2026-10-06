package be.stib.monitoring.model;

import java.time.Instant;

/**
 * A ticket control reported by a traveller at a stop.
 *
 * @param stop      name and position of the stop, so it can be shown without loading a line; null if unknown
 * @param lineId    line the reporter said the control concerns; null when not specified
 * @param message   optional free text from the reporter; null when none was given
 * @param expiresAt when the report stops being shown
 */
public record TicketControl(
        String id,
        String stopId,
        StopDetails stop,
        String lineId,
        ControlType type,
        String message,
        Instant reportedAt,
        Instant expiresAt) {
}
