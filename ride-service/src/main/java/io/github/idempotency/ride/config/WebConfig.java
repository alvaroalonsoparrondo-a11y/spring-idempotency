package io.github.idempotency.ride.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.user.UserRepository;
import io.github.idempotency.ride.web.CallerResolvingFilter;
import io.github.idempotency.ride.web.JsonBodyGuardFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class WebConfig {

    /**
     * Ahead of idempify's response filter, which is registered at the lowest precedence and replays recorded
     * responses before any handler runs.
     */
    static final int CALLER_FILTER_ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    /**
     * After the caller is known, still ahead of idempify's response filter.
     */
    static final int JSON_BODY_GUARD_ORDER = Ordered.LOWEST_PRECEDENCE - 50;

    @Bean
    public FilterRegistrationBean<CallerResolvingFilter> callerResolvingFilter(UserRepository userRepository,
                                                                               ObjectMapper objectMapper,
                                                                               Clock clock) {
        FilterRegistrationBean<CallerResolvingFilter> registration = new FilterRegistrationBean<>(
                new CallerResolvingFilter(userRepository, objectMapper, clock)
        );
        registration.setOrder(CALLER_FILTER_ORDER);
        registration.addUrlPatterns("/api/v1/rides", "/api/v1/rides/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JsonBodyGuardFilter> jsonBodyGuardFilter(ObjectMapper objectMapper, Clock clock) {
        FilterRegistrationBean<JsonBodyGuardFilter> registration = new FilterRegistrationBean<>(
                new JsonBodyGuardFilter(objectMapper, clock)
        );
        registration.setOrder(JSON_BODY_GUARD_ORDER);
        registration.addUrlPatterns("/api/v1/rides", "/api/v1/rides/*");
        return registration;
    }
}
