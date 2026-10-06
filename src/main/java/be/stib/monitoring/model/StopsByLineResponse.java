package be.stib.monitoring.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** Raw response of the STIB {@code static/stopsByLine} dataset. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StopsByLineResponse(
        @JsonProperty("total_count") Integer totalCount,
        List<Record> results) {

    /**
     * One route (line + direction). {@code destination} and {@code points} are
     * delivered by the API as JSON-encoded strings, so they are kept as raw nodes
     * and decoded by the client.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Record(
            String lineid,
            String direction,
            JsonNode destination,
            JsonNode points) {
    }
}
