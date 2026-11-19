package io.github.idempotency.ride.config;

import io.github.idempotency.ride.booking.ChargeLockVerifier;
import io.github.idempotency.ride.booking.FareProperties;
import io.github.idempotency.ride.stripe.StripeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class BookingConfigIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(StripeConfig.class, BookingConfig.class)
            .withPropertyValues("stripe.api-key=sk_test_123", "spring.datasource.hikari.connection-timeout=5000");

    @Test
    @DisplayName("IT context when no fare is set should charge 20 dollars per ride")
    void context_whenNoFareIsSet_shouldChargeTwentyDollarsPerRide() {
        runner.run(context -> {
            // then
            assertThat(context.getBean(FareProperties.class)).isEqualTo(new FareProperties(2000L, "usd"));
        });
    }

    @Test
    @DisplayName("IT context when the fare is set should bind amount and currency from their keys")
    void context_whenFareIsSet_shouldBindAmountAndCurrencyFromTheirKeys() {
        runner.withPropertyValues("rides.fare.amount=1500", "rides.fare.currency=eur").run(context -> {
            // then
            assertThat(context.getBean(FareProperties.class)).isEqualTo(new FareProperties(1500L, "eur"));
        });
    }

    @Test
    @DisplayName("IT context when the amount is not positive should fail to start naming the property")
    void context_whenAmountIsNotPositive_shouldFailToStartNamingProperty() {
        runner.withPropertyValues("rides.fare.amount=0").run(context -> {
            // then
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("amount");
        });
    }

    @Test
    @DisplayName("IT context when the currency is not a lowercase ISO code should fail to start naming the property")
    void context_whenCurrencyIsNotLowercaseIsoCode_shouldFailToStartNamingProperty() {
        runner.withPropertyValues("rides.fare.currency=USD").run(context -> {
            // then
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("currency");
        });
    }

    @Test
    @DisplayName("IT context when the connection wait would let a booking outlast its lock should fail to start")
    void context_whenConnectionWaitWouldLetBookingOutlastLock_shouldFailToStart() {
        runner.withPropertyValues("spring.datasource.hikari.connection-timeout=30000").run(context -> {
            // then
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("idempotency lock");
        });
    }

    @Test
    @DisplayName("IT context when the timeouts fit inside the lock should start with the verifier")
    void context_whenTimeoutsFitInsideLock_shouldStartWithVerifier() {
        runner.withPropertyValues("spring.datasource.hikari.connection-timeout=5000").run(context -> {
            // then
            assertThat(context).hasSingleBean(ChargeLockVerifier.class);
        });
    }
}
