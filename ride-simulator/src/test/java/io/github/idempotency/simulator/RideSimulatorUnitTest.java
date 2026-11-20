package io.github.idempotency.simulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.github.idempotency.simulator.RideSimulator.MAX_ATTEMPTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideSimulatorUnitTest {

    private static final SimulatorProperties PROPERTIES = new SimulatorProperties(
            "http://localhost:8080",
            List.of("first@example.com", "second@example.com")
    );
    private static final BookingAnswer BOOKED = new BookingAnswer(201, "{\"id\":7}");
    private static final BookingAnswer IN_PROCESS = new BookingAnswer(409, "{}");

    private final RecordingRideClient client = new RecordingRideClient();
    private final List<Duration> pauses = new ArrayList<>();

    @Test
    @DisplayName("UT bookOnce() when the first answer arrives should book once and not retry")
    void bookOnce_whenFirstAnswerArrives_shouldBookOnceAndNotRetry() {
        // given
        client.answering(BOOKED);
        RideSimulator tested = simulator(1, 0.9);

        // when
        Optional<BookingAnswer> answer = tested.bookOnce();

        // then
        assertThat(answer).contains(BOOKED);
        assertThat(client.attempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.rider()).isEqualTo("second@example.com");
            assertThat(attempt.hungUp()).isFalse();
            assertThat(attempt.body()).contains("\"origin\"").contains("\"target\"");
        });
        assertThat(pauses).isEmpty();
    }

    @Test
    @DisplayName("UT bookOnce() when the first answer is to be lost should hang up, then retry under the same key")
    void bookOnce_whenFirstAnswerIsToBeLost_shouldHangUpAndRetryUnderSameKeyUntilBooked() {
        // given
        client.answering(BOOKED);
        RideSimulator tested = simulator(0, 0.1);

        // when
        Optional<BookingAnswer> answer = tested.bookOnce();

        // then
        assertThat(answer).contains(BOOKED);
        assertThat(client.attempts()).hasSize(2);
        assertThat(client.attempts().get(0).hungUp()).isTrue();
        assertThat(client.attempts().get(1).hungUp()).isFalse();
        assertThat(client.attempts().get(1).idempotencyKey()).isEqualTo(client.attempts().get(0).idempotencyKey());
        assertThat(client.attempts().get(1).body()).isEqualTo(client.attempts().get(0).body());
    }

    @Test
    @DisplayName("UT bookOnce() when the booking is still in process should back off and retry under the same key")
    void bookOnce_whenBookingIsStillInProcess_shouldBackOffAndRetryUnderSameKey() {
        // given
        client.answering(IN_PROCESS).answering(IN_PROCESS).answering(BOOKED);
        RideSimulator tested = simulator(0, 0.9);

        // when
        Optional<BookingAnswer> answer = tested.bookOnce();

        // then
        assertThat(answer).contains(BOOKED);
        assertThat(pauses).containsExactly(Duration.ofMillis(200), Duration.ofMillis(400));
    }

    @Test
    @DisplayName("UT bookOnce() when the answer is a refusal should take it as definitive and not retry")
    void bookOnce_whenAnswerIsRefusal_shouldTakeItAsDefinitiveAndNotRetry() {
        // given
        BookingAnswer declined = new BookingAnswer(402, "{\"type\":\"payment-declined\"}");
        client.answering(declined);
        RideSimulator tested = simulator(0, 0.9);

        // when
        Optional<BookingAnswer> answer = tested.bookOnce();

        // then
        assertThat(answer).contains(declined);
        assertThat(client.attempts()).hasSize(1);
    }

    @Test
    @DisplayName("UT bookOnce() when no definitive answer ever comes should give up after the last attempt")
    void bookOnce_whenNoDefinitiveAnswerEverComes_shouldGiveUpAfterLastAttempt() {
        // given
        RideSimulator tested = simulator(0, 0.9);

        // when
        Optional<BookingAnswer> answer = tested.bookOnce();

        // then
        assertThat(answer).isEmpty();
        assertThat(client.attempts()).hasSize(MAX_ATTEMPTS);
        assertThat(pauses).hasSize(MAX_ATTEMPTS).last().isEqualTo(Duration.ofMillis(200L << (MAX_ATTEMPTS - 1)));
    }

    @Test
    @DisplayName("UT constructor when client is null should throw NullPointerException")
    void constructor_whenClientIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideSimulator(null, PROPERTIES))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("client cannot be null");
    }

    @Test
    @DisplayName("UT constructor when properties is null should throw NullPointerException")
    void constructor_whenPropertiesIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideSimulator(client, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("properties cannot be null");
    }

    @Test
    @DisplayName("UT constructor when random is null should throw NullPointerException")
    void constructor_whenRandomIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideSimulator(client, PROPERTIES, null, pauses::add))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("random cannot be null");
    }

    @Test
    @DisplayName("UT constructor when sleeper is null should throw NullPointerException")
    void constructor_whenSleeperIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideSimulator(client, PROPERTIES, new ScriptedRandom(0, 0.9), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sleeper cannot be null");
    }

    private RideSimulator simulator(int riderIndex, double failureDraw) {
        return new RideSimulator(client, PROPERTIES, new ScriptedRandom(riderIndex, failureDraw), pauses::add);
    }
}
