package io.github.idempotency.simulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideClientUnitTest {

    @Test
    @DisplayName("UT constructor when properties is null should throw NullPointerException")
    void constructor_whenPropertiesIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RideClient(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("properties cannot be null");
    }
}
