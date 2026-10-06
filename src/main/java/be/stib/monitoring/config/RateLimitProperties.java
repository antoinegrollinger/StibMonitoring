package be.stib.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-client-IP limits on the API.
 *
 * @param enabled           whether the limits apply
 * @param requestsPerMinute every {@code /api} call; bursts up to this many are allowed
 * @param reportsPerHour    ticket control reports, on top of {@code requestsPerMinute}
 * @param clientIpHeader    header holding the client IP when the app runs behind a proxy, e.g.
 *                          {@code X-Forwarded-For} (its last value is used) or {@code CF-Connecting-IP};
 *                          blank to use the connection's address. Only set it when a proxy always
 *                          sets this header, as clients could otherwise spoof it.
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(boolean enabled, int requestsPerMinute, int reportsPerHour, String clientIpHeader) {
}
