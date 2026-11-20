package io.github.idempotency.simulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestConstructor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "simulator.run=false")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RideSimulatorApplicationIntegrationTest {

    private final ApplicationContext context;

    RideSimulatorApplicationIntegrationTest(ApplicationContext context) {
        this.context = context;
    }

    @Test
    @DisplayName("IT context when started with the defaults should wire the simulator against a local ride-service")
    void context_whenStartedWithDefaults_shouldWireSimulatorAgainstLocalRideService() {
        // then
        assertThat(context.getBean(RideSimulator.class)).isNotNull();
        assertThat(context.getBean(SimulatorProperties.class)).isEqualTo(new SimulatorProperties(
                "http://localhost:8080",
                List.of("local.user@example.com", "second.user@example.com", "third.user@example.com")
        ));
        assertThat(context.getBeansOfType(SimulatorRunner.class)).isEmpty();
    }
}
