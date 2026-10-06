package be.stib.monitoring.web;

import be.stib.monitoring.client.StibClient;
import be.stib.monitoring.config.ClockConfig;
import be.stib.monitoring.config.ControlProperties;
import be.stib.monitoring.model.LocalizedName;
import be.stib.monitoring.model.StopDetails;
import be.stib.monitoring.service.LineStopsService;
import be.stib.monitoring.service.TicketControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketControlController.class)
@Import({TicketControlControllerTest.Config.class, TicketControlService.class, ClockConfig.class})
@TestPropertySource(properties = {"controls.ttl=30m", "controls.max-message-length=280"})
class TicketControlControllerTest {

    @EnableConfigurationProperties(ControlProperties.class)
    static class Config {
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    StibClient stibClient;

    @MockitoBean
    LineStopsService lineStops;

    @BeforeEach
    void stops() {
        when(stibClient.getAllStopDetails()).thenReturn(Map.of("8042",
                new StopDetails("8042", new LocalizedName("Arts-Loi", "Kunst-Wet"), 50.8455, 4.3695)));
    }

    @Test
    void reportedControlsAreListed() throws Exception {
        mvc.perform(post("/api/stops/8042/controls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"POLICE\",\"lineId\":\"5\",\"message\":\"At the exit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].stopId").value("8042"))
                .andExpect(jsonPath("$[0].type").value("POLICE"));

        mvc.perform(get("/api/controls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stopId").value("8042"))
                .andExpect(jsonPath("$[0].lineId").value("5"))
                .andExpect(jsonPath("$[0].stop.name.fr").value("Arts-Loi"))
                .andExpect(jsonPath("$[0].stop.latitude").value(50.8455))
                .andExpect(jsonPath("$[0].message").value("At the exit"));
    }

    @Test
    void rejectsAReportWithoutType() throws Exception {
        mvc.perform(post("/api/stops/8042/controls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isBadRequest());
    }
}
