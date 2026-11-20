package io.github.idempotency.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RideSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideSimulatorApplication.class, args);
    }
}
