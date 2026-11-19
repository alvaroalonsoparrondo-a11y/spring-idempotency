package io.github.idempotency.ride.config;

import io.github.idempotency.ride.booking.ChargeLockVerifier;
import io.github.idempotency.ride.booking.FareProperties;
import io.github.idempotency.ride.stripe.StripeProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FareProperties.class)
public class BookingConfig {

    /**
     * Hikari's own default when the property is not set is 30 seconds.
     */
    @Bean
    public ChargeLockVerifier chargeLockVerifier(StripeProperties stripe,
                                                 @Value("${spring.datasource.hikari.connection-timeout:30000}")
                                                 long connectionTimeoutMillis) {
        return new ChargeLockVerifier(stripe, Duration.ofMillis(connectionTimeoutMillis));
    }
}
