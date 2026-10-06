package be.stib.monitoring.client;

import be.stib.monitoring.config.CacheConfig;
import be.stib.monitoring.config.StibProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringJUnitConfig(StibClientCachingTest.Config.class)
class StibClientCachingTest {

    @Configuration
    @Import(CacheConfig.class)
    static class Config {

        final RestClient.Builder builder = RestClient.builder().baseUrl("https://stib.test/api");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        @Bean
        StibProperties stibProperties() {
            return new StibProperties("https://stib.test/api", "t",
                    new StibProperties.Cache(Duration.ofMinutes(5), Duration.ofHours(1), Duration.ofMillis(200)));
        }

        @Bean
        MockRestServiceServer server() {
            return server;
        }

        @Bean
        StibClient stibClient() {
            return new StibClient(builder.build(), new ObjectMapper());
        }
    }

    @Autowired
    StibClient client;

    @Autowired
    MockRestServiceServer server;

    @Test
    void callsTheTravellersInformationApiOnlyOnceWithinTheTtl() {
        server.expect(ExpectedCount.once(), requestTo("https://stib.test/api/rt/TravellersInformation/"))
                .andRespond(withSuccess("""
                        {"results":[{"id":"1","priority":4,"content":"[]","lines":"[]","points":"[]"}],"total_count":1}
                        """, MediaType.APPLICATION_JSON));

        client.getTravellersInformation();
        client.getTravellersInformation();

        server.verify();
        assertThat(client.getTravellersInformation()).hasSize(1);
    }

    @Test
    void reusesLiveDataWhileItIsFreshThenFetchesItAgain() throws InterruptedException {
        server.expect(ExpectedCount.times(2), requestTo("https://stib.test/api/rt/VehiclePositions/"))
                .andRespond(withSuccess("{\"results\":[{\"lineid\":\"1\",\"vehiclepositions\":[]}]}",
                        MediaType.APPLICATION_JSON));

        client.getAllVehiclePositions();
        client.getAllVehiclePositions(); // within the 200 ms live TTL: served from the cache
        Thread.sleep(300);
        client.getAllVehiclePositions();

        server.verify();
    }
}
