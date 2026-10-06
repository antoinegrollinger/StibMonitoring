package be.stib.monitoring.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final Level originalLevel = logger.getLevel();

    @AfterEach
    void restoreLevel() {
        logger.setLevel(originalLevel);
    }

    @Test
    void logsCallsAtDebugLevel(CapturedOutput output) throws Exception {
        logger.setLevel(Level.DEBUG);

        call();

        assertThat(output).containsPattern("GET /api/lines/live\\?ids=1,5 -> 200 in \\d+ ms \\(client 1\\.2\\.3\\.4\\)");
    }

    @Test
    void staysSilentAboveDebugLevel(CapturedOutput output) throws Exception {
        logger.setLevel(Level.INFO);

        call();

        assertThat(output).doesNotContain("/api/lines/live");
    }

    private static void call() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/lines/live");
        request.setQueryString("ids=1,5");
        request.setRemoteAddr("1.2.3.4");
        new RequestLoggingFilter(null).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}
