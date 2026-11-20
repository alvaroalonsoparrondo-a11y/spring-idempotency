package io.github.idempotency.simulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorPropertiesIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesOnly.class);

    @Test
    @DisplayName("IT context when the base url and riders are set should bind each from its key")
    void context_whenBaseUrlAndRidersAreSet_shouldBindEachFromItsKey() {
        runner.withPropertyValues(
                "simulator.base-url=http://ride-service:8080",
                "simulator.riders=one@example.com,two@example.com"
        ).run(context -> {
            // then
            assertThat(context.getBean(SimulatorProperties.class)).isEqualTo(new SimulatorProperties(
                    "http://ride-service:8080",
                    List.of("one@example.com", "two@example.com")
            ));
        });
    }

    @Test
    @DisplayName("IT context when the base url is blank should fail to start naming the property")
    void context_whenBaseUrlIsBlank_shouldFailToStartNamingProperty() {
        runner.withPropertyValues("simulator.base-url= ").run(context -> {
            // then
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("baseUrl");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SimulatorProperties.class)
    static class PropertiesOnly {
    }
}
