package io.github.idempotency.ride.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * The app the ride was booked from, for support and analytics.
 *
 * @param appVersion the version of the app
 * @param platform   the platform the app runs on, for example ios or android
 */
@Schema(description = "App the ride is booked from")
public record ClientInfo(
        @Schema(
                description = "Version of the app",
                example = "4.2.0",
                maxLength = 32,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 32, message = "appVersion must be at most {max} characters")
        String appVersion,

        @Schema(
                description = "Platform the app runs on",
                example = "android",
                maxLength = 32,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 32, message = "platform must be at most {max} characters")
        String platform
) {
}
