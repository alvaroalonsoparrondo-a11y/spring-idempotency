package io.github.idempotency.contract;

import java.time.Instant;
import java.util.Objects;

/**
 * A receipt owed to a rider for a charged ride, staged in the transaction that records the charge and delivered to
 * the receipt service through Kafka.
 * <p>
 * The event id travels in the message headers, not here: a redelivery carries the same id and is skipped by the
 * consumer, while two receipts for one ride can only come from two different rides.
 *
 * @param rideId         the ride that was charged
 * @param userId         the rider the receipt goes to
 * @param stripeChargeId the Stripe charge the receipt confirms
 * @param amount         the charged amount in the smallest currency unit, positive
 * @param currency       the lowercase ISO 4217 code, as Stripe reports it
 * @param chargedAt      when the charge was recorded
 */
public record ReceiptJob(long rideId,
                         long userId,
                         String stripeChargeId,
                         long amount,
                         String currency,
                         Instant chargedAt) {

    /**
     * The event type the job is published and consumed under.
     */
    public static final String EVENT_TYPE = "ride-receipt";

    public ReceiptJob {
        Objects.requireNonNull(stripeChargeId, "stripeChargeId cannot be null");
        Objects.requireNonNull(currency, "currency cannot be null");
        Objects.requireNonNull(chargedAt, "chargedAt cannot be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, was %d".formatted(amount));
        }
    }
}
