package be.stib.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for traveller-reported ticket controls.
 *
 * @param ttl              how long a report is shown after it was made
 * @param maxMessageLength maximum length of the optional message
 */
@ConfigurationProperties(prefix = "controls")
public record ControlProperties(Duration ttl, int maxMessageLength) {
}
