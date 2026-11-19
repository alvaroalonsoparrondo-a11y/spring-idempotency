package io.github.idempotency.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptJobUnitTest {

    private static final Instant CHARGED_AT = Instant.parse("2026-11-18T10:15:30Z");

    @Test
    @DisplayName("UT constructor when every component is valid should keep them as given")
    void constructor_whenEveryComponentIsValid_shouldKeepThemAsGiven() {
        // when
        ReceiptJob tested = new ReceiptJob(7L, 3L, "ch_123", 2000L, "usd", CHARGED_AT);

        // then
        assertThat(tested.rideId()).isEqualTo(7L);
        assertThat(tested.userId()).isEqualTo(3L);
        assertThat(tested.stripeChargeId()).isEqualTo("ch_123");
        assertThat(tested.amount()).isEqualTo(2000L);
        assertThat(tested.currency()).isEqualTo("usd");
        assertThat(tested.chargedAt()).isEqualTo(CHARGED_AT);
    }

    @Test
    @DisplayName("UT constructor when stripeChargeId is null should throw NullPointerException")
    void constructor_whenStripeChargeIdIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ReceiptJob(7L, 3L, null, 2000L, "usd", CHARGED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stripeChargeId cannot be null");
    }

    @Test
    @DisplayName("UT constructor when currency is null should throw NullPointerException")
    void constructor_whenCurrencyIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ReceiptJob(7L, 3L, "ch_123", 2000L, null, CHARGED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currency cannot be null");
    }

    @Test
    @DisplayName("UT constructor when chargedAt is null should throw NullPointerException")
    void constructor_whenChargedAtIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ReceiptJob(7L, 3L, "ch_123", 2000L, "usd", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("chargedAt cannot be null");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("UT constructor when amount is not positive should throw IllegalArgumentException")
    void constructor_whenAmountIsNotPositive_shouldThrowIllegalArgumentException(long amount) {
        // when / then
        assertThatThrownBy(() -> new ReceiptJob(7L, 3L, "ch_123", amount, "usd", CHARGED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be positive, was %d".formatted(amount));
    }
}
