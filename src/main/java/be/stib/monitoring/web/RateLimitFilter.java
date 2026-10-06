package be.stib.monitoring.web;

import be.stib.monitoring.config.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Limits each client IP to {@link RateLimitProperties#requestsPerMinute()} API calls, and to
 * {@link RateLimitProperties#reportsPerHour()} ticket control reports. Over the limit, answers 429
 * with a {@code Retry-After} header. Token buckets: a client may burst up to the limit, then gets
 * tokens back at a steady rate. Buckets are kept in memory and forgotten after an hour of inactivity.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern REPORT_PATH = Pattern.compile("/api/stops/[^/]+/controls");

    private final RateLimitProperties properties;
    private final LongSupplier nanoTime;
    private final Cache<String, TokenBucket> requests = buckets();
    private final Cache<String, TokenBucket> reports = buckets();

    public RateLimitFilter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    RateLimitFilter(RateLimitProperties properties, LongSupplier nanoTime) {
        this.properties = properties;
        this.nanoTime = nanoTime;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = ClientAddress.of(request, properties.clientIpHeader());
        long now = nanoTime.getAsLong();
        long wait = requests.get(ip, k -> new TokenBucket(properties.requestsPerMinute(), Duration.ofMinutes(1), now))
                .tryConsume(now);
        if (wait == 0 && "POST".equals(request.getMethod()) && REPORT_PATH.matcher(request.getRequestURI()).matches()) {
            wait = reports.get(ip, k -> new TokenBucket(properties.reportsPerHour(), Duration.ofHours(1), now))
                    .tryConsume(now);
        }
        if (wait > 0) {
            reject(response, wait);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, long waitNanos) throws IOException {
        long seconds = Math.max(1, Duration.ofNanos(waitNanos).toSeconds() + 1);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(seconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Too many requests from this address, retry in %d s"}""".formatted(seconds));
    }

    private static Cache<String, TokenBucket> buckets() {
        return Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(1)).maximumSize(100_000).build();
    }

    /** Holds up to {@code capacity} tokens, refilled at {@code capacity} per {@code period}. */
    static final class TokenBucket {

        private final double capacity;
        private final double tokensPerNano;
        private double tokens;
        private long updatedAt;

        TokenBucket(int capacity, Duration period, long now) {
            this.capacity = capacity;
            this.tokensPerNano = capacity / (double) period.toNanos();
            this.tokens = capacity;
            this.updatedAt = now;
        }

        /** Takes a token: returns 0 on success, or the nanoseconds until one is available. */
        synchronized long tryConsume(long now) {
            tokens = Math.min(capacity, tokens + (now - updatedAt) * tokensPerNano);
            updatedAt = now;
            if (tokens >= 1) {
                tokens--;
                return 0;
            }
            return (long) Math.ceil((1 - tokens) / tokensPerNano);
        }
    }
}
