package be.stib.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StibMonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(StibMonitoringApplication.class, args);
    }
}
