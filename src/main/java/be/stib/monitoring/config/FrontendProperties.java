package be.stib.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings handed to the frontend through {@code GET /api/config}.
 *
 * @param refreshInterval default auto-refresh interval of the live view; zero disables auto-refresh.
 *                        Users can override it in the UI.
 */
@ConfigurationProperties(prefix = "frontend")
public record FrontendProperties(Duration refreshInterval) {
}
