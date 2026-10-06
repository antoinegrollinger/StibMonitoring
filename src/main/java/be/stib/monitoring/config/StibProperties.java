package be.stib.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "stib")
public record StibProperties(String baseUrl, String token, Cache cache) {

    /**
     * @param messagesTtl how long traveller information messages are cached
     * @param staticTtl   how long static datasets (stops by line, stop details) are cached
     * @param liveTtl     how long real-time data (vehicle positions, waiting times) is reused, so
     *                    that visitors refreshing at the same time share one STIB call
     */
    public record Cache(Duration messagesTtl, Duration staticTtl, Duration liveTtl) {
    }
}
