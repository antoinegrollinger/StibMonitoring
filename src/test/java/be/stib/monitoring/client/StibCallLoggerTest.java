package be.stib.monitoring.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class StibCallLoggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(StibCallLogger.class);
    private final Level originalLevel = logger.getLevel();

    @AfterEach
    void restoreLevel() {
        logger.setLevel(originalLevel);
    }

    @Test
    void logsStibCallsWithoutTheToken(CapturedOutput output) {
        logger.setLevel(Level.DEBUG);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://stib.test/api")
                .defaultHeader("bmc-partner-key", "secret-token")
                .requestInterceptor(new StibCallLogger());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://stib.test/api/rt/VehiclePositions/"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        builder.build().get().uri("/rt/VehiclePositions/").retrieve().toBodilessEntity();

        assertThat(output).containsPattern("STIB GET https://stib.test/api/rt/VehiclePositions/ -> 200 in \\d+ ms");
        assertThat(output).doesNotContain("secret-token");
    }
}
