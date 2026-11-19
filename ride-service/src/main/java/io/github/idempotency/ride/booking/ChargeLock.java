package io.github.idempotency.ride.booking;

import io.github.idempotency.ride.stripe.StripeProperties;

import java.time.Duration;

/**
 * The lock a booking holds on its idempotency key while it charges. It has to outlast the slowest booking, or a
 * repeat takes the key over and runs the booking a second time next to the first; the configuration that bounds
 * the booking is checked against it at startup, so a timeout raised in config cannot quietly break that.
 */
public final class ChargeLock {

    /**
     * The lock, in minutes, as {@code @Idempotent} takes it.
     */
    public static final long MINUTES = 2;

    static final Duration TTL = Duration.ofMinutes(MINUTES);

    /**
     * The longest pause the Stripe SDK makes between two network retries.
     */
    static final Duration STRIPE_RETRY_DELAY = Duration.ofSeconds(5);

    /**
     * A booking takes a pooled connection twice: to store the ride and to record its charge.
     */
    static final int CONNECTIONS_PER_BOOKING = 2;

    private ChargeLock() {}

    /**
     * @param stripe         how long one Stripe call may take and how often it is retried
     * @param connectionWait how long the pool lets a booking wait for a connection
     * @return the longest a booking can run under this configuration
     */
    static Duration worstBooking(StripeProperties stripe, Duration connectionWait) {
        int attempts = stripe.maxNetworkRetries() + 1;
        Duration stripeCalls = stripe.connectTimeout().plus(stripe.readTimeout()).multipliedBy(attempts);
        Duration stripePauses = STRIPE_RETRY_DELAY.multipliedBy(stripe.maxNetworkRetries());
        return stripeCalls.plus(stripePauses).plus(connectionWait.multipliedBy(CONNECTIONS_PER_BOOKING));
    }

    /**
     * @throws IllegalStateException when a booking could outlast the lock
     */
    static void verify(StripeProperties stripe, Duration connectionWait) {
        Duration worst = worstBooking(stripe, connectionWait);
        if (worst.compareTo(TTL) >= 0) {
            throw new IllegalStateException(("A booking can take up to %s under the stripe.* timeouts and retries and "
                    + "spring.datasource.hikari.connection-timeout, not less than its idempotency lock of %s: lower "
                    + "them").formatted(worst, TTL));
        }
    }
}
