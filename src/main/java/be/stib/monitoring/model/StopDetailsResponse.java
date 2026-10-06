package be.stib.monitoring.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** Raw response of the STIB {@code static/StopDetails} dataset. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StopDetailsResponse(
        @JsonProperty("total_count") Integer totalCount,
        List<Record> results) {

    /** {@code name} and {@code gpscoordinates} are JSON-encoded strings. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Record(
            String id,
            JsonNode name,
            JsonNode gpscoordinates) {
    }
}
