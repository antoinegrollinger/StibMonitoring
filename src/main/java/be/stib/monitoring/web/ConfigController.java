package be.stib.monitoring.web;

import be.stib.monitoring.config.FrontendProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    public record FrontendConfig(long refreshIntervalSeconds) {
    }

    private final FrontendProperties properties;

    public ConfigController(FrontendProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public FrontendConfig config() {
        return new FrontendConfig(properties.refreshInterval().toSeconds());
    }
}
