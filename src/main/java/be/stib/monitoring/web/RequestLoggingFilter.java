package be.stib.monitoring.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs every API call with its status, duration and client address, at DEBUG level only: when
 * DEBUG is off for this logger the filter is skipped entirely.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final String clientIpHeader;

    public RequestLoggingFilter(String clientIpHeader) {
        this.clientIpHeader = clientIpHeader;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !log.isDebugEnabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String call = request.getMethod() + " " + request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        String client = ClientAddress.of(request, clientIpHeader);
        try {
            chain.doFilter(request, response);
            log.debug("{} -> {} in {} ms (client {})", call, response.getStatus(), elapsedMillis(start), client);
        } catch (IOException | ServletException | RuntimeException e) {
            log.debug("{} -> failed with {} after {} ms (client {})", call, e.getClass().getSimpleName(),
                    elapsedMillis(start), client);
            throw e;
        }
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
