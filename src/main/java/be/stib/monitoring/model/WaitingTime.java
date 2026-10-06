package be.stib.monitoring.model;

import java.time.OffsetDateTime;

/**
 * Next expected passage of a line at a stop.
 *
 * @param message optional remark from STIB (e.g. "Theoretical time" when no live data is available)
 */
public record WaitingTime(
        String lineId,
        LocalizedName destination,
        OffsetDateTime expectedArrivalTime,
        LocalizedText message) {
}
