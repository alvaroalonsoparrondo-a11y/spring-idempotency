package io.github.idempotency.ride.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * A booked ride as stored.
 *
 * @param id             the id of the ride
 * @param userId         the user who booked it
 * @param idempotencyKey the key of the request that booked it
 * @param origin         where the ride starts
 * @param target         where the ride ends
 * @param amount         the fare in the smallest currency unit
 * @param currency       the lowercase ISO 4217 code of the fare
 * @param stripeChargeId the Stripe charge that paid the fare; {@code null} until the charge is confirmed
 * @param chargedAt      when the charge was recorded; {@code null} exactly when {@code stripeChargeId} is
 * @param createdAt      when the ride was booked
 */
public record Ride(long id,
                   long userId,
                   UUID idempotencyKey,
                   Coordinates origin,
                   Coordinates target,
                   long amount,
                   String currency,
                   String stripeChargeId,
                   Instant chargedAt,
                   Instant createdAt) {

    public boolean isCharged() {
        return stripeChargeId != null;
    }
}
