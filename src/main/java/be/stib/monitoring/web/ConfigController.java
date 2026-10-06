package be.stib.monitoring.web;

import be.stib.monitoring.config.FrontendProperties;
import be.stib.monitoring.config.StibProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    /**
     * @param messagesRefreshSeconds how often the frontend reloads traveller messages: the backend
     *                               caches them this long ({@code stib.cache.messages-ttl}), so
     *                               asking more often would only return the same messages
     */
    public record FrontendConfig(long refreshIntervalSeconds, boolean mergeDirections, long messagesRefreshSeconds) {
    }

    private final FrontendProperties properties;
    private final StibProperties stibProperties;

    public ConfigController(FrontendProperties properties, StibProperties stibProperties) {
        this.properties = properties;
        this.stibProperties = stibProperties;
    }

    @GetMapping
    public FrontendConfig config() {
        return new FrontendConfig(properties.refreshInterval().toSeconds(), properties.mergeDirections(),
                stibProperties.cache().messagesTtl().toSeconds());
    }
}
