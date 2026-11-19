package io.github.idempotency.ride.booking;

import java.time.Instant;

/**
 * A booked and charged ride.
 *
 * @param id             the id of the ride
 * @param origin         where the ride starts
 * @param target         where the ride ends
 * @param amount         the fare in the smallest currency unit
 * @param currency       the lowercase ISO 4217 code of the fare
 * @param stripeChargeId the Stripe charge that paid the fare
 * @param chargedAt      when the charge was recorded
 * @param createdAt      when the ride was booked
 */
public record RideResponse(long id,
                           Coordinates origin,
                           Coordinates target,
                           long amount,
                           String currency,
                           String stripeChargeId,
                           Instant chargedAt,
                           Instant createdAt) {

    static RideResponse from(Ride ride) {
        return new RideResponse(
                ride.id(),
                ride.origin(),
                ride.target(),
                ride.amount(),
                ride.currency(),
                ride.stripeChargeId(),
                ride.chargedAt(),
                ride.createdAt()
        );
    }
}
