package be.stib.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "stib")
public record StibProperties(String baseUrl, String token, Cache cache) {

    /**
     * @param messagesTtl how long traveller information messages are cached
     * @param staticTtl   how long static datasets (stops by line, stop details) are cached
     */
    public record Cache(Duration messagesTtl, Duration staticTtl) {
    }
}
