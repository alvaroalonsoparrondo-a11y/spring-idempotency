package io.github.idempotency.ride.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.user.UserRepository;
import io.github.idempotency.ride.web.CallerResolvingFilter;
import io.github.idempotency.ride.web.CorrelationIdFilter;
import io.github.idempotency.ride.web.JsonBodyGuardFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebConfigUnitTest {

    @Test
    @DisplayName("UT callerResolvingFilter() should run on the ride paths ahead of the lowest-precedence filters")
    void callerResolvingFilter_whenRegistered_shouldRunOnRidePathsAheadOfLowestPrecedence() {
        // when
        FilterRegistrationBean<CallerResolvingFilter> tested = new WebConfig().callerResolvingFilter(
                mock(UserRepository.class),
                new ObjectMapper(),
                Clock.systemUTC()
        );

        // then
        assertThat(tested.getUrlPatterns()).containsExactly("/api/v1/rides", "/api/v1/rides/*");
        assertThat(tested.getOrder()).isLessThan(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    @DisplayName("UT jsonBodyGuardFilter() should run on the ride paths after the caller filter and ahead of idempify's")
    void jsonBodyGuardFilter_whenRegistered_shouldRunAfterCallerFilterAndAheadOfLowestPrecedence() {
        // when
        FilterRegistrationBean<JsonBodyGuardFilter> tested = new WebConfig().jsonBodyGuardFilter(
                new ObjectMapper(),
                Clock.systemUTC()
        );

        // then
        assertThat(tested.getUrlPatterns()).containsExactly("/api/v1/rides", "/api/v1/rides/*");
        assertThat(tested.getOrder()).isGreaterThan(WebConfig.CALLER_FILTER_ORDER)
                .isLessThan(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    @DisplayName("UT correlationIdFilter() should run on every path ahead of the filters that log")
    void correlationIdFilter_whenRegistered_shouldRunOnEveryPathAheadOfFiltersThatLog() {
        // when
        FilterRegistrationBean<CorrelationIdFilter> tested = new WebConfig().correlationIdFilter();

        // then
        assertThat(tested.getUrlPatterns()).containsExactly("/*");
        assertThat(tested.getOrder()).isLessThan(WebConfig.CALLER_FILTER_ORDER);
    }
}
