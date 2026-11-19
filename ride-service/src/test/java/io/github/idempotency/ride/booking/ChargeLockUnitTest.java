package io.github.idempotency.ride.booking;

import io.github.idempotency.ride.stripe.StripeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeLockUnitTest {

    private static final StripeProperties DEFAULTS = stripe(Duration.ofSeconds(5), Duration.ofSeconds(20), 2);

    @Test
    @DisplayName("UT worstBooking() should add every Stripe attempt, the pauses between them and two connection waits")
    void worstBooking_whenComputed_shouldAddStripeAttemptsPausesAndTwoConnectionWaits() {
        // when
        Duration worst = ChargeLock.worstBooking(DEFAULTS, Duration.ofSeconds(5));

        // then: 3 x (5s + 20s) + 2 x 5s + 2 x 5s
        assertThat(worst).isEqualTo(Duration.ofSeconds(95));
    }

    @Test
    @DisplayName("UT verify() when the shipped defaults are used should accept them")
    void verify_whenShippedDefaultsAreUsed_shouldAcceptThem() {
        // when / then
        assertThatCode(() -> ChargeLock.verify(DEFAULTS, Duration.ofSeconds(5))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT verify() when Hikari's default connection wait is kept should refuse the configuration")
    void verify_whenHikariDefaultConnectionWaitIsKept_shouldRefuseConfiguration() {
        // when / then
        assertThatThrownBy(() -> ChargeLock.verify(DEFAULTS, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PT2M25S")
                .hasMessageContaining("PT2M");
    }

    @Test
    @DisplayName("UT verify() when a booking would take exactly as long as the lock should refuse the configuration")
    void verify_whenBookingWouldTakeExactlyAsLongAsLock_shouldRefuseConfiguration() {
        // given: 1 x (60s + 50s) + 2 x 5s
        StripeProperties stripe = stripe(Duration.ofSeconds(60), Duration.ofSeconds(50), 0);

        // when / then
        assertThatThrownBy(() -> ChargeLock.verify(stripe, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("UT afterSingletonsInstantiated() when a booking could outlast the lock should stop the startup")
    void afterSingletonsInstantiated_whenBookingCouldOutlastLock_shouldStopStartup() {
        // given
        ChargeLockVerifier tested = new ChargeLockVerifier(DEFAULTS, Duration.ofMinutes(1));

        // when / then
        assertThatThrownBy(tested::afterSingletonsInstantiated).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("UT constructor when stripe is null should throw NullPointerException")
    void constructor_whenStripeIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ChargeLockVerifier(null, Duration.ofSeconds(5)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stripe cannot be null");
    }

    @Test
    @DisplayName("UT constructor when connectionWait is null should throw NullPointerException")
    void constructor_whenConnectionWaitIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ChargeLockVerifier(DEFAULTS, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("connectionWait cannot be null");
    }

    private static StripeProperties stripe(Duration connect, Duration read, int retries) {
        return new StripeProperties("sk_test_123", "http://localhost:12111", connect, read, retries);
    }
}
