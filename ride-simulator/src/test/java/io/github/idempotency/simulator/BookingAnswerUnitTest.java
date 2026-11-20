package io.github.idempotency.simulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BookingAnswerUnitTest {

    @ParameterizedTest
    @ValueSource(ints = {201, 400, 401, 402, 422, 500, 502, 503})
    @DisplayName("UT isDefinitive() when the answer is the booking's outcome should be definitive")
    void isDefinitive_whenAnswerIsBookingOutcome_shouldBeDefinitive(int status) {
        // when / then
        assertThat(new BookingAnswer(status, "{}").isDefinitive()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {409})
    @DisplayName("UT isDefinitive() when the first attempt under the key is still running should not be definitive")
    void isDefinitive_whenFirstAttemptUnderKeyIsStillRunning_shouldNotBeDefinitive(int status) {
        // when / then
        assertThat(new BookingAnswer(status, "{}").isDefinitive()).isFalse();
    }
}
