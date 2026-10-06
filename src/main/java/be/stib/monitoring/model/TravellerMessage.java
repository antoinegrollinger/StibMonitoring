package be.stib.monitoring.model;

import java.util.List;

/**
 * A service message (works, diversions, events...) published by STIB.
 *
 * @param priority as given by STIB; lower values are more important
 * @param lineIds  lines the message is about (may be empty for stop-only messages)
 * @param stopIds  stops the message is about
 */
public record TravellerMessage(
        String id,
        int priority,
        LocalizedText text,
        List<String> lineIds,
        List<String> stopIds) {
}
