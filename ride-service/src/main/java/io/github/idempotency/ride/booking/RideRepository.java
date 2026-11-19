package io.github.idempotency.ride.booking;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RideRepository {

    private static final String COLUMNS = """
            id, user_id, idempotency_key, origin_lat, origin_lon, target_lat, target_lon, amount, currency,
            stripe_charge_id, charged_at, created_at
            """;

    private static final RowMapper<Ride> ROW_MAPPER = (rs, rowNum) -> new Ride(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getObject("idempotency_key", UUID.class),
            new Coordinates(rs.getBigDecimal("origin_lat"), rs.getBigDecimal("origin_lon")),
            new Coordinates(rs.getBigDecimal("target_lat"), rs.getBigDecimal("target_lon")),
            rs.getLong("amount"),
            rs.getString("currency"),
            rs.getString("stripe_charge_id"),
            toInstant(rs.getTimestamp("charged_at")),
            toInstant(rs.getTimestamp("created_at"))
    );

    private final JdbcClient jdbc;

    public RideRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    /**
     * @return the stored ride, or empty when a ride already holds its idempotency key
     */
    Optional<Ride> insertIfAbsent(NewRide ride) {
        return jdbc.sql("""
                        INSERT INTO rides (user_id, idempotency_key, origin_lat, origin_lon, target_lat, target_lon,
                                           amount, currency)
                        VALUES (:userId, :key, :originLat, :originLon, :targetLat, :targetLon, :amount, :currency)
                        ON CONFLICT (idempotency_key) DO NOTHING
                        RETURNING
                        """ + COLUMNS)
                .param("userId", ride.userId())
                .param("key", ride.idempotencyKey())
                .param("originLat", ride.origin().lat())
                .param("originLon", ride.origin().lon())
                .param("targetLat", ride.target().lat())
                .param("targetLon", ride.target().lon())
                .param("amount", ride.amount())
                .param("currency", ride.currency())
                .query(ROW_MAPPER)
                .optional();
    }

    Optional<Ride> findByIdempotencyKey(UUID idempotencyKey) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM rides WHERE idempotency_key = ?")
                .param(idempotencyKey)
                .query(ROW_MAPPER)
                .optional();
    }

    /**
     * Records the charge on a ride. Recording the same charge again changes nothing and answers the ride as it is:
     * two runs of one booking - the second taking over after the first one's lock expired - get the same charge from
     * Stripe, and whichever records it second must not fail.
     *
     * @return the charged ride, with the time its charge was first recorded
     * @throws IllegalStateException when the ride does not exist or carries another charge
     */
    Ride markCharged(long rideId, String stripeChargeId, Instant chargedAt) {
        return jdbc.sql("""
                        UPDATE rides
                        SET stripe_charge_id = :chargeId, charged_at = COALESCE(charged_at, :chargedAt)
                        WHERE id = :id AND (stripe_charge_id IS NULL OR stripe_charge_id = :chargeId)
                        RETURNING
                        """ + COLUMNS)
                .param("chargeId", stripeChargeId)
                .param("chargedAt", Timestamp.from(chargedAt))
                .param("id", rideId)
                .query(ROW_MAPPER)
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "Ride %d is missing or carries another charge".formatted(rideId)
                ));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
