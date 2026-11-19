package io.github.idempotency.ride.booking;

import io.github.idempotency.ride.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class TestRides {

    static final UUID KEY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    static final User USER = new User(3L, "rider@example.com", "cus_rider");
    static final Coordinates ORIGIN = new Coordinates(new BigDecimal("52.5200066"), new BigDecimal("13.4049540"));
    static final Coordinates TARGET = new Coordinates(new BigDecimal("52.3675734"), new BigDecimal("4.9041389"));
    static final Instant CREATED_AT = Instant.parse("2026-11-18T10:15:00Z");
    static final Instant CHARGED_AT = Instant.parse("2026-11-18T10:15:30Z");

    private TestRides() {}

    static CreateRideRequest request() {
        return new CreateRideRequest(ORIGIN, TARGET, new ClientInfo("4.2.0", "ios"));
    }

    static Ride uncharged(long id) {
        return new Ride(id, USER.id(), KEY, ORIGIN, TARGET, 2000L, "usd", null, null, CREATED_AT);
    }

    static Ride charged(long id, String chargeId) {
        return new Ride(id, USER.id(), KEY, ORIGIN, TARGET, 2000L, "usd", chargeId, CHARGED_AT, CREATED_AT);
    }
}
