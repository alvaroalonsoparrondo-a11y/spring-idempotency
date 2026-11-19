package io.github.idempotency.ride.stripe;

import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestImages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@RequiresDocker
class StripeChargeClientIntegrationTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    @Container
    static final GenericContainer<?> STRIPE_MOCK = new GenericContainer<>(TestImages.STRIPE_MOCK)
            .withExposedPorts(TestImages.STRIPE_MOCK_PORT)
            .waitingFor(Wait.forListeningPort());

    @Test
    @DisplayName("IT charge() when stripe-mock accepts the charge should return a charge id")
    void charge_whenStripeMockAcceptsCharge_shouldReturnChargeId() {
        // given
        StripeChargeClient tested = client(stripeMockUrl());

        // when
        String chargeId = tested.charge("cus_rider", 2000L, "usd", "Ride 7", KEY);

        // then
        assertThat(chargeId).startsWith("ch_");
    }

    @Test
    @DisplayName("IT charge() when nothing listens at the api base should throw PaymentProviderException")
    void charge_whenNothingListensAtApiBase_shouldThrowPaymentProviderException() {
        // given
        StripeChargeClient tested = client("http://localhost:1");

        // when / then
        assertThatThrownBy(() -> tested.charge("cus_rider", 2000L, "usd", "Ride 7", KEY))
                .isInstanceOf(PaymentProviderException.class);
    }

    private static StripeChargeClient client(String apiBase) {
        StripeProperties properties = new StripeProperties(
                "sk_test_123",
                apiBase,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                0
        );
        return new StripeChargeClient(new StripeConfig().stripeClient(properties));
    }

    private static String stripeMockUrl() {
        return "http://%s:%d".formatted(
                STRIPE_MOCK.getHost(),
                STRIPE_MOCK.getMappedPort(TestImages.STRIPE_MOCK_PORT)
        );
    }
}
