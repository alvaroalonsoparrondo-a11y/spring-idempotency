package io.github.idempotency.ride.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * A ride to book from one point to another.
 *
 * @param origin where the ride starts
 * @param target where the ride ends
 * @param client the app the ride is booked from; optional
 */
@Schema(description = "Ride to book")
public record CreateRideRequest(
        @Schema(description = "Where the ride starts", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "origin must not be null")
        @Valid
        Coordinates origin,

        @Schema(description = "Where the ride ends", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "target must not be null")
        @Valid
        Coordinates target,

        @Schema(description = "App the ride is booked from", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Valid
        ClientInfo client
) {
}
