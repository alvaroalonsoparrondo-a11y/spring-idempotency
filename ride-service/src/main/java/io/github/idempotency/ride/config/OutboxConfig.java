package io.github.idempotency.ride.config;

import io.github.dmitriyiliyov.oncebox.starter.EnableOncebox;
import org.springframework.context.annotation.Configuration;

/**
 * Switches the oncebox outbox on in a configuration of its own rather than on the application class, so web slice
 * tests, which leave configurations out, do not start it.
 */
@Configuration(proxyBeanMethods = false)
@EnableOncebox
public class OutboxConfig {
}
