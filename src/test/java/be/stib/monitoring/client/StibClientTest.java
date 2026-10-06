package be.stib.monitoring.client;

import be.stib.monitoring.model.LineStops;
import be.stib.monitoring.model.StopDetails;
import be.stib.monitoring.model.TravellerMessage;
import be.stib.monitoring.model.VehiclePosition;
import be.stib.monitoring.model.WaitingTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StibClientTest {

    private static final String BODY = """
            {
              "totalCount": 2,
              "results": [{
                "lineid": "1",
                "direction": "City",
                "destination": "{\\"fr\\": \\"GARE DE L'OUEST\\", \\"nl\\": \\"WESTSTATION\\"}",
                "points": "[{\\"id\\": \\"8742\\", \\"order\\": 2}, {\\"id\\": \\"8733\\", \\"order\\": 1}]"
              }, {
                "lineid": "5",
                "direction": "Suburb",
                "destination": "{\\"fr\\": \\"ERASME\\", \\"nl\\": \\"ERASMUS\\"}",
                "points": "[{\\"id\\": \\"8742\\", \\"order\\": 1}]"
              }]
            }
            """;

    @Test
    void fetchesAndGroupsStopsByLineForTheWholeNetwork() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://stib.test/api")
                .defaultHeader("bmc-partner-key", "secret");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://stib.test/api/static/stopsByLine/"))
                .andExpect(header("bmc-partner-key", "secret"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        Map<String, List<LineStops>> result = new StibClient(builder.build(), new ObjectMapper()).getAllStopsByLine();

        server.verify();
        assertThat(result).containsOnlyKeys("1", "5");
        assertThat(result.get("1")).singleElement().satisfies(line -> {
            assertThat(line.lineId()).isEqualTo("1");
            assertThat(line.direction()).isEqualTo("City");
            assertThat(line.destination().nl()).isEqualTo("WESTSTATION");
            assertThat(line.stops()).extracting(LineStops.Stop::id).containsExactly("8733", "8742");
        });
    }

    @Test
    void fetchesAllStopDetailsKeyedById() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://stib.test/api");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://stib.test/api/static/StopDetails/"))
                .andRespond(withSuccess("""
                        {"results":[{"gpscoordinates":"{\\"latitude\\":50.818151,\\"longitude\\":4.350715}",
                                     "id":"5021","name":"{\\"fr\\":\\"DARWIN\\",\\"nl\\":\\"DARWIN\\"}"}],
                         "total_count":1}
                        """, MediaType.APPLICATION_JSON));

        Map<String, StopDetails> result =
                new StibClient(builder.build(), new ObjectMapper()).getAllStopDetails();

        server.verify();
        assertThat(result).containsOnlyKeys("5021");
        StopDetails darwin = result.get("5021");
        assertThat(darwin.name().fr()).isEqualTo("DARWIN");
        assertThat(darwin.latitude()).isEqualTo(50.818151);
        assertThat(darwin.longitude()).isEqualTo(4.350715);
    }

    @Test
    void fetchesVehiclePositionsForTheWholeNetworkGroupedByLine() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://stib.test/api");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://stib.test/api/rt/VehiclePositions/"))
                .andRespond(withSuccess("""
                        {"results":[{"lineid":"1","vehiclepositions":"[{\\"directionId\\":\\"8731\\",\\"distanceFromPoint\\":0,\\"pointId\\":\\"8031\\"},{\\"directionId\\":\\"8161\\",\\"distanceFromPoint\\":120,\\"pointId\\":\\"8742\\"}]"},
                                    {"lineid":"5","vehiclepositions":"[{\\"directionId\\":\\"8641\\",\\"distanceFromPoint\\":3,\\"pointId\\":\\"8742\\"}]"}],
                         "totalCount":2}
                        """, MediaType.APPLICATION_JSON));

        Map<String, List<VehiclePosition>> result =
                new StibClient(builder.build(), new ObjectMapper()).getAllVehiclePositions();

        server.verify();
        assertThat(result.get("1")).containsExactly(
                new VehiclePosition("8731", "8031", 0),
                new VehiclePosition("8161", "8742", 120));
        assertThat(result.get("5")).containsExactly(new VehiclePosition("8641", "8742", 3));
    }

    @Test
    void fetchesWaitingTimesForAllLinesSoonestFirst() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://stib.test/api");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://stib.test/api/rt/WaitingTimes/")))
                .andExpect(queryParam("where", "pointid%3D8742"))
                .andRespond(withSuccess("""
                        {"results":[
                          {"pointid":"8742","lineid":"1","passingtimes":"[{\\"destination\\":{\\"fr\\":\\"STOCKEL\\",\\"nl\\":\\"STOKKEL\\"},\\"expectedArrivalTime\\":\\"2026-10-06T22:20:00+02:00\\",\\"lineId\\":\\"1\\"}]"},
                          {"pointid":"8742","lineid":"5","passingtimes":"[{\\"destination\\":{\\"fr\\":\\"HERRMANN-DEBROUX\\",\\"nl\\":\\"HERRMANN-DEBROUX\\"},\\"expectedArrivalTime\\":\\"2026-10-06T22:15:00+02:00\\",\\"lineId\\":\\"5\\",\\"message\\":{\\"fr\\":\\"Temps théorique\\",\\"en\\":\\"Theoretical time\\"}}]"}],
                         "total_count":2}
                        """, MediaType.APPLICATION_JSON));

        List<WaitingTime> result = new StibClient(builder.build(), new ObjectMapper()).getWaitingTimes("8742");

        server.verify();
        assertThat(result).extracting(WaitingTime::lineId).containsExactly("5", "1");
        assertThat(result.getFirst().message().en()).isEqualTo("Theoretical time");
        assertThat(result.getFirst().message().fr()).isEqualTo("Temps théorique");
        assertThat(result.getLast().destination().nl()).isEqualTo("STOKKEL");
        assertThat(result.getLast().message()).isNull();
    }

    @Test
    void fetchesTravellersInformationWithoutFilter() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://stib.test/api");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://stib.test/api/rt/TravellersInformation/"))
                .andRespond(withSuccess("""
                        {"results":[{"id":"42","type":"LongText","priority":4,
                          "content":"[{\\"text\\":[{\\"en\\":\\"Works.\\",\\"fr\\":\\"Travaux.\\",\\"nl\\":\\"Werken.\\"}],\\"type\\":\\"Description\\"}]",
                          "lines":"[{\\"id\\":\\"1\\"}]",
                          "points":"[{\\"id\\":\\"8742\\"},{\\"id\\":\\"8733\\"}]"}],
                         "total_count":1}
                        """, MediaType.APPLICATION_JSON));

        List<TravellerMessage> result = new StibClient(builder.build(), new ObjectMapper()).getTravellersInformation();

        server.verify();
        assertThat(result).singleElement().satisfies(m -> {
            assertThat(m.id()).isEqualTo("42");
            assertThat(m.priority()).isEqualTo(4);
            assertThat(m.text().en()).isEqualTo("Works.");
            assertThat(m.text().nl()).isEqualTo("Werken.");
            assertThat(m.lineIds()).containsExactly("1");
            assertThat(m.stopIds()).containsExactly("8742", "8733");
        });
    }
}
