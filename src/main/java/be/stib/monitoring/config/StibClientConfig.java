package be.stib.monitoring.config;

import be.stib.monitoring.client.StibCallLogger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class StibClientConfig {

    static final String PARTNER_KEY_HEADER = "bmc-partner-key";

    @SuppressWarnings("null")
    @Bean
    RestClient stibRestClient(RestClient.Builder builder, StibProperties properties) {
        if (!StringUtils.hasText(properties.token())) {
            throw new IllegalStateException(
                    "STIB API token missing: set the STIB-TOKEN (or STIB_TOKEN) environment variable");
        }
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(PARTNER_KEY_HEADER, properties.token())
                .requestInterceptor(new StibCallLogger())
                .build();
    }
}
