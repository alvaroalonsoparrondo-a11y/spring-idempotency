package io.github.idempotency.ride.stripe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * How the service reaches Stripe. The timeouts and retries together bound the longest charge, which the idempotency
 * lock of a ride has to outlast.
 *
 * @param apiKey            the secret key
 * @param apiBase           the API base url; stripe-mock's url locally and in tests
 * @param connectTimeout    the time to open a connection; never zero, which the SDK reads as no timeout at all
 * @param readTimeout       the time to wait for an answer; never zero, as above
 * @param maxNetworkRetries the retries the SDK makes on a network failure, each under the same idempotency key
 */
@Validated
@ConfigurationProperties("stripe")
public record StripeProperties(@NotBlank String apiKey,
                               @NotBlank @DefaultValue("https://api.stripe.com") String apiBase,
                               @NotNull @DurationMin(millis = 1) @DefaultValue("5s") Duration connectTimeout,
                               @NotNull @DurationMin(millis = 1) @DefaultValue("20s") Duration readTimeout,
                               @PositiveOrZero @DefaultValue("2") int maxNetworkRetries) {
}
