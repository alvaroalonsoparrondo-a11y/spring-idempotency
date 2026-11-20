package io.github.idempotency.simulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatorRunnerUnitTest {

    @Test
    @Timeout(10)
    @DisplayName("UT run() should keep booking until its thread is interrupted")
    void run_whenStarted_shouldKeepBookingUntilThreadIsInterrupted() {
        // given
        CountingSimulator simulator = new CountingSimulator(3);
        SimulatorRunner tested = new SimulatorRunner(simulator);

        // when / then
        assertThatThrownBy(tested::run).isInstanceOf(InterruptedException.class);
        assertThat(simulator.bookings()).isEqualTo(3);
        assertThat(Thread.interrupted()).isFalse();
    }

    /**
     * Books nothing; interrupts its own thread on the booking it is told to, as a stop signal would.
     */
    private static final class CountingSimulator extends RideSimulator {

        private final int interruptOn;
        private final AtomicInteger bookings = new AtomicInteger();

        private CountingSimulator(int interruptOn) {
            super(new RecordingRideClient(), new SimulatorProperties("http://localhost:8080", List.of("a@example.com")));
            this.interruptOn = interruptOn;
        }

        int bookings() {
            return bookings.get();
        }

        @Override
        public Optional<BookingAnswer> bookOnce() {
            if (bookings.incrementAndGet() == interruptOn) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }
}
