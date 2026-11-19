package io.github.idempotency.ride.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The fare every ride is charged, whatever its distance.
 *
 * @param amount   the fare in the smallest currency unit
 * @param currency the lowercase ISO 4217 code
 */
@Validated
@ConfigurationProperties("rides.fare")
public record FareProperties(@Positive @DefaultValue("2000") long amount,
                             @NotBlank @Pattern(regexp = "[a-z]{3}") @DefaultValue("usd") String currency) {
}
