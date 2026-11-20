package io.github.idempotency.ride.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * A point on the map in decimal degrees, kept to ten fractional digits.
 */
@Schema(description = "Point on the map in decimal degrees")
public record Coordinates(
        @Schema(
                description = "Latitude in decimal degrees, at most ten fractional digits",
                example = "52.5200066",
                minimum = "-90",
                maximum = "90",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "lat must not be null")
        @DecimalMin(value = "-90", message = "lat must be at least {value}")
        @DecimalMax(value = "90", message = "lat must be at most {value}")
        @Digits(integer = 3, fraction = 10, message = "lat must have at most {fraction} fractional digits")
        BigDecimal lat,

        @Schema(
                description = "Longitude in decimal degrees, at most ten fractional digits",
                example = "13.4049540",
                minimum = "-180",
                maximum = "180",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "lon must not be null")
        @DecimalMin(value = "-180", message = "lon must be at least {value}")
        @DecimalMax(value = "180", message = "lon must be at most {value}")
        @Digits(integer = 3, fraction = 10, message = "lon must have at most {fraction} fractional digits")
        BigDecimal lon
) {

    /**
     * @return whether both points are the same place, whatever the scale the numbers were written with
     */
    boolean samePlaceAs(Coordinates other) {
        return lat.compareTo(other.lat) == 0 && lon.compareTo(other.lon) == 0;
    }
}
