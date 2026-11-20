package io.github.idempotency.simulator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Where the simulator books and for whom.
 *
 * @param baseUrl the ride-service to book against
 * @param riders  the emails of the riders it books for, users ride-service knows
 */
@Validated
@ConfigurationProperties("simulator")
public record SimulatorProperties(
        @NotBlank @DefaultValue("http://localhost:8080") String baseUrl,
        @NotEmpty @DefaultValue({"local.user@example.com", "second.user@example.com", "third.user@example.com"})
        List<String> riders
) {
}
