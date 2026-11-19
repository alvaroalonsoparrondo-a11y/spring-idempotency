package io.github.idempotency.ride.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinatesUnitTest {

    private static final Coordinates BERLIN = new Coordinates(new BigDecimal("52.52"), new BigDecimal("13.405"));

    @Test
    @DisplayName("UT samePlaceAs() when the numbers differ only in scale should be the same place")
    void samePlaceAs_whenNumbersDifferOnlyInScale_shouldBeSamePlace() {
        // given
        Coordinates rescaled = new Coordinates(new BigDecimal("52.5200000000"), new BigDecimal("13.4050"));

        // when / then
        assertThat(BERLIN.samePlaceAs(rescaled)).isTrue();
    }

    @Test
    @DisplayName("UT samePlaceAs() when the latitude differs should not be the same place")
    void samePlaceAs_whenLatitudeDiffers_shouldNotBeSamePlace() {
        // given
        Coordinates north = new Coordinates(new BigDecimal("52.53"), new BigDecimal("13.405"));

        // when / then
        assertThat(BERLIN.samePlaceAs(north)).isFalse();
    }

    @Test
    @DisplayName("UT samePlaceAs() when the longitude differs should not be the same place")
    void samePlaceAs_whenLongitudeDiffers_shouldNotBeSamePlace() {
        // given
        Coordinates east = new Coordinates(new BigDecimal("52.52"), new BigDecimal("13.406"));

        // when / then
        assertThat(BERLIN.samePlaceAs(east)).isFalse();
    }
}
