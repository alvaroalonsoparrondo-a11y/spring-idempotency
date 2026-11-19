package io.github.idempotency.ride.booking;

import io.github.idempotency.ride.stripe.StripeProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.time.Duration;
import java.util.Objects;

/**
 * Refuses to start the service when a booking could outlast its {@link ChargeLock}.
 */
public class ChargeLockVerifier implements SmartInitializingSingleton {

    private final StripeProperties stripe;
    private final Duration connectionWait;

    public ChargeLockVerifier(StripeProperties stripe, Duration connectionWait) {
        this.stripe = Objects.requireNonNull(stripe, "stripe cannot be null");
        this.connectionWait = Objects.requireNonNull(connectionWait, "connectionWait cannot be null");
    }

    @Override
    public void afterSingletonsInstantiated() {
        ChargeLock.verify(stripe, connectionWait);
    }
}
