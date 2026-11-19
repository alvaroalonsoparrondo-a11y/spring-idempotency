package io.github.idempotency.ride.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * A ride to book from one point to another.
 *
 * @param origin where the ride starts
 * @param target where the ride ends
 * @param client the app the ride is booked from; optional
 */
public record CreateRideRequest(
        @NotNull(message = "origin must not be null")
        @Valid
        Coordinates origin,

        @NotNull(message = "target must not be null")
        @Valid
        Coordinates target,

        @Valid
        ClientInfo client
) {
}
