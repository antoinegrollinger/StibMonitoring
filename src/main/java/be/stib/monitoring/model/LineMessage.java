package be.stib.monitoring.model;

import java.util.List;

/**
 * A traveller message relevant to one or more of the requested lines.
 *
 * @param lineIds         the requested lines the message concerns
 * @param affectedStopIds the stops of those lines the message mentions (empty if it is about the lines as a whole)
 */
public record LineMessage(
        String id,
        int priority,
        LocalizedText text,
        List<String> lineIds,
        List<String> affectedStopIds) {
}
