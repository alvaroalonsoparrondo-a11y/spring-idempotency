package io.github.idempotency.ride.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.user.UserRepository;
import io.github.idempotency.ride.web.CallerResolvingFilter;
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
}
