package io.github.idempotency.ride.web;

import io.github.dmitriyiliyov.idempify.http.IdempifyControllerAdvice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;

/**
 * Registers idempify's own advice ahead of {@link RideControllerAdvice}. idempify declares it without an order, and
 * an advice with a catch-all that ties with it in order would answer the idempotency refusals as a 500; registered
 * here, the auto-configured one steps aside.
 */
@Configuration(proxyBeanMethods = false)
public class IdempifyAdviceConfig {

    @Bean
    public IdempifyControllerAdvice idempifyControllerAdvice(Clock clock) {
        return new OrderedIdempifyControllerAdvice(clock);
    }

    static final class OrderedIdempifyControllerAdvice extends IdempifyControllerAdvice implements Ordered {

        OrderedIdempifyControllerAdvice(Clock clock) {
            super(clock);
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE - 1;
        }
    }
}
