package io.github.idempotency.ride.booking;

import jakarta.validation.constraints.Size;

/**
 * The app the ride was booked from, for support and analytics.
 *
 * @param appVersion the version of the app
 * @param platform   the platform the app runs on, for example ios or android
 */
public record ClientInfo(
        @Size(max = 32, message = "appVersion must be at most {max} characters")
        String appVersion,

        @Size(max = 32, message = "platform must be at most {max} characters")
        String platform
) {
}
