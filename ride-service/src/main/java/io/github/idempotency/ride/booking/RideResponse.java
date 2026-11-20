package io.github.idempotency.ride.booking;

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Booked and charged ride")
public record RideResponse(
        @Schema(description = "Id of the ride", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(description = "Where the ride starts", requiredMode = Schema.RequiredMode.REQUIRED)
        Coordinates origin,

        @Schema(description = "Where the ride ends", requiredMode = Schema.RequiredMode.REQUIRED)
        Coordinates target,

        @Schema(
                description = "Fare in the smallest currency unit",
                example = "2000",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long amount,

        @Schema(description = "Lowercase ISO 4217 code of the fare", example = "usd",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String currency,

        @Schema(description = "Stripe charge that paid the fare", example = "ch_3QXkzP2eZvKYlo2C0x8Gv7Lm",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String stripeChargeId,

        @Schema(description = "When the charge was recorded", example = "2026-11-18T10:15:30Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant chargedAt,

        @Schema(description = "When the ride was booked", example = "2026-11-18T10:15:29Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt
) {

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
