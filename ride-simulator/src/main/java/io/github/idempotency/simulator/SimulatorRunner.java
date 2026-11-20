package io.github.idempotency.simulator;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * Books rides one after another, with a pause of up to two seconds between them, until the process is stopped.
 * {@code simulator.run=false} leaves the application started and idle.
 */
@Component
@ConditionalOnProperty(name = "simulator.run", havingValue = "true", matchIfMissing = true)
public class SimulatorRunner implements CommandLineRunner {

    private final RideSimulator simulator;
    private final RandomGenerator random = RandomGenerator.getDefault();

    public SimulatorRunner(RideSimulator simulator) {
        this.simulator = simulator;
    }

    @Override
    public void run(String... args) throws InterruptedException {
        while (!Thread.currentThread().isInterrupted()) {
            simulator.bookOnce();
            Thread.sleep(Duration.ofMillis(random.nextLong(2000)));
        }
    }
}
