package be.stib.monitoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injectable so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
