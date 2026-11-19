package io.github.idempotency.ride.web;

import io.github.idempotency.ride.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerUnitTest {

    @Test
    @DisplayName("UT constructor when user is null should throw NullPointerException")
    void constructor_whenUserIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new Caller(null, "203.0.113.7"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("user cannot be null");
    }

    @Test
    @DisplayName("UT constructor when originIp is null should throw NullPointerException")
    void constructor_whenOriginIpIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new Caller(new User(3L, "rider@example.com", "cus_rider"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("originIp cannot be null");
    }
}
