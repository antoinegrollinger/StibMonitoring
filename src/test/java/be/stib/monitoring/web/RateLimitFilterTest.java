package be.stib.monitoring.web;

import be.stib.monitoring.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private long now = 0;

    private RateLimitFilter filter(String clientIpHeader) {
        return new RateLimitFilter(new RateLimitProperties(true, 3, 1, clientIpHeader), () -> now);
    }

    @Test
    void allowsABurstUpToTheLimitThenAsksToRetry() throws Exception {
        RateLimitFilter filter = filter(null);
        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "GET", "/api/controls", "1.1.1.1", null).getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse rejected = call(filter, "GET", "/api/controls", "1.1.1.1", null);

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("21"); // a token every 20 s
        assertThat(rejected.getContentAsString()).contains("\"status\":429");
    }

    @Test
    void refillsOverTime() throws Exception {
        RateLimitFilter filter = filter(null);
        for (int i = 0; i < 3; i++) {
            call(filter, "GET", "/api/controls", "1.1.1.1", null);
        }

        now += Duration.ofSeconds(20).toNanos();

        assertThat(call(filter, "GET", "/api/controls", "1.1.1.1", null).getStatus()).isEqualTo(200);
        assertThat(call(filter, "GET", "/api/controls", "1.1.1.1", null).getStatus()).isEqualTo(429);
    }

    @Test
    void countsEachAddressSeparately() throws Exception {
        RateLimitFilter filter = filter(null);
        for (int i = 0; i < 3; i++) {
            call(filter, "GET", "/api/controls", "1.1.1.1", null);
        }

        assertThat(call(filter, "GET", "/api/controls", "2.2.2.2", null).getStatus()).isEqualTo(200);
    }

    @Test
    void limitsReportsSeparately() throws Exception {
        RateLimitFilter filter = filter(null);

        assertThat(call(filter, "POST", "/api/stops/8042/controls", "1.1.1.1", null).getStatus()).isEqualTo(200);
        assertThat(call(filter, "POST", "/api/stops/8042/controls", "1.1.1.1", null).getStatus()).isEqualTo(429);
        assertThat(call(filter, "GET", "/api/controls", "1.1.1.1", null).getStatus()).isEqualTo(200);
    }

    @Test
    void usesTheLastValueOfTheConfiguredHeader() throws Exception {
        RateLimitFilter filter = filter("X-Forwarded-For");
        for (int i = 0; i < 3; i++) {
            // A client faking the first value still lands in the bucket of the address the proxy saw.
            call(filter, "GET", "/api/controls", "10.0.0.1", "spoofed-" + i + ", 3.3.3.3");
        }

        assertThat(call(filter, "GET", "/api/controls", "10.0.0.1", "3.3.3.3").getStatus()).isEqualTo(429);
        assertThat(call(filter, "GET", "/api/controls", "10.0.0.1", "4.4.4.4").getStatus()).isEqualTo(200);
    }

    @Test
    void leavesNonApiPathsAlone() throws Exception {
        RateLimitFilter filter = filter(null);
        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "GET", "/index.html", "1.1.1.1", null).getStatus()).isEqualTo(200);
        }
    }

    private static MockHttpServletResponse call(RateLimitFilter filter, String method, String path, String remoteAddr,
                                                String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
