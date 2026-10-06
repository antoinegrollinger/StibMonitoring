package be.stib.monitoring.web;

import be.stib.monitoring.config.FrontendProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigController.class)
@Import(ConfigControllerTest.Config.class)
@TestPropertySource(properties = "frontend.refresh-interval=30s")
class ConfigControllerTest {

    @EnableConfigurationProperties(FrontendProperties.class)
    static class Config {
    }

    @Autowired
    MockMvc mvc;

    @Test
    void exposesTheConfiguredRefreshIntervalInSeconds() throws Exception {
        mvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshIntervalSeconds").value(30));
    }
}
