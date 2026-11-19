package io.github.idempotency.ride.config;

import io.github.idempotency.ride.booking.FareProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FareProperties.class)
public class BookingConfig {
}
