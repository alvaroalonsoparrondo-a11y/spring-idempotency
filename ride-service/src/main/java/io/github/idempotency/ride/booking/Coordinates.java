package io.github.idempotency.ride.booking;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * A point on the map in decimal degrees, kept to ten fractional digits.
 */
public record Coordinates(
        @NotNull(message = "lat must not be null")
        @DecimalMin(value = "-90", message = "lat must be at least {value}")
        @DecimalMax(value = "90", message = "lat must be at most {value}")
        @Digits(integer = 3, fraction = 10, message = "lat must have at most {fraction} fractional digits")
        BigDecimal lat,

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
