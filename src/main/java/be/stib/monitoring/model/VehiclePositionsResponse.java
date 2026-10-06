package be.stib.monitoring.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** Raw response of the STIB {@code rt/VehiclePositions} dataset. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VehiclePositionsResponse(
        @JsonProperty("total_count") Integer totalCount,
        List<Record> results) {

    /** {@code vehiclepositions} is a JSON-encoded string. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Record(
            String lineid,
            JsonNode vehiclepositions) {
    }
}
