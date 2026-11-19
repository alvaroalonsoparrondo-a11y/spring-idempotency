package io.github.idempotency.contract;

/**
 * Names of the structured log fields both services write, so a ride is followed across them by one query on the
 * same key.
 */
public final class LogFields {

    public static final String CORRELATION_ID = "correlationId";

    public static final String IDEMPOTENCY_KEY = "idempotencyKey";

    public static final String USER_ID = "userId";

    public static final String RIDE_ID = "rideId";

    public static final String STRIPE_CHARGE_ID = "stripeChargeId";

    public static final String EVENT_ID = "eventId";

    private LogFields() {}
}
