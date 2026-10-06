package be.stib.monitoring.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** Raw response of the STIB {@code rt/TravellersInformation} dataset. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TravellersInformationResponse(
        @JsonProperty("total_count") Integer totalCount,
        List<Record> results) {

    /** {@code content}, {@code lines} and {@code points} are JSON-encoded strings. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Record(
            String id,
            String type,
            Integer priority,
            JsonNode content,
            JsonNode lines,
            JsonNode points) {
    }
}
