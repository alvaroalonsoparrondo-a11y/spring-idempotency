package io.github.idempotency.ride.booking;

import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestImages;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.github.idempotency.ride.booking.TestRides.CHARGED_AT;
import static io.github.idempotency.ride.booking.TestRides.KEY;
import static io.github.idempotency.ride.booking.TestRides.ORIGIN;
import static io.github.idempotency.ride.booking.TestRides.TARGET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@RequiresDocker
class RideRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TestImages.POSTGRES);

    private static JdbcClient jdbc;

    private final RideRepository tested = new RideRepository(jdbc);
    private long userId;

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE audit_records, rides, users RESTART IDENTITY CASCADE").update();
        userId = jdbc.sql("INSERT INTO users (email, stripe_customer_id) VALUES ('rider@example.com', 'cus_rider') "
                        + "RETURNING id")
                .query(Long.class)
                .single();
    }

    @Test
    @DisplayName("IT insertIfAbsent() when the key is new should store an uncharged ride and return it")
    void insertIfAbsent_whenKeyIsNew_shouldStoreUnchargedRideAndReturnIt() {
        // when
        Optional<Ride> stored = tested.insertIfAbsent(newRide(KEY));

        // then
        assertThat(stored).hasValueSatisfying(ride -> {
            assertThat(ride.id()).isPositive();
            assertThat(ride.userId()).isEqualTo(userId);
            assertThat(ride.idempotencyKey()).isEqualTo(KEY);
            assertThat(ride.origin().samePlaceAs(ORIGIN)).isTrue();
            assertThat(ride.target().samePlaceAs(TARGET)).isTrue();
            assertThat(ride.amount()).isEqualTo(2000L);
            assertThat(ride.currency()).isEqualTo("usd");
            assertThat(ride.stripeChargeId()).isNull();
            assertThat(ride.chargedAt()).isNull();
            assertThat(ride.createdAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("IT insertIfAbsent() when a ride already holds the key should return empty and store nothing")
    void insertIfAbsent_whenRideAlreadyHoldsKey_shouldReturnEmptyAndStoreNothing() {
        // given
        tested.insertIfAbsent(newRide(KEY));

        // when
        Optional<Ride> stored = tested.insertIfAbsent(newRide(KEY));

        // then
        assertThat(stored).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM rides").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @DisplayName("IT findByIdempotencyKey() when a ride holds the key should return it")
    void findByIdempotencyKey_whenRideHoldsKey_shouldReturnIt() {
        // given
        Ride stored = tested.insertIfAbsent(newRide(KEY)).orElseThrow();

        // when
        Optional<Ride> found = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(found).contains(stored);
    }

    @Test
    @DisplayName("IT findByIdempotencyKey() when no ride holds the key should return empty")
    void findByIdempotencyKey_whenNoRideHoldsKey_shouldReturnEmpty() {
        // when
        Optional<Ride> found = tested.findByIdempotencyKey(UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("IT markCharged() when the ride has no charge yet should record the charge and its time")
    void markCharged_whenRideHasNoChargeYet_shouldRecordChargeAndItsTime() {
        // given
        Ride stored = tested.insertIfAbsent(newRide(KEY)).orElseThrow();

        // when
        Ride charged = tested.markCharged(stored.id(), "ch_123", CHARGED_AT);

        // then
        assertThat(charged.stripeChargeId()).isEqualTo("ch_123");
        assertThat(charged.chargedAt()).isEqualTo(CHARGED_AT);
        assertThat(charged.isCharged()).isTrue();
        assertThat(tested.findByIdempotencyKey(KEY)).contains(charged);
    }

    @Test
    @DisplayName("IT markCharged() when the ride is already charged should throw and keep the first charge")
    void markCharged_whenRideIsAlreadyCharged_shouldThrowAndKeepFirstCharge() {
        // given
        Ride stored = tested.insertIfAbsent(newRide(KEY)).orElseThrow();
        tested.markCharged(stored.id(), "ch_first", CHARGED_AT);

        // when / then
        assertThatThrownBy(() -> tested.markCharged(stored.id(), "ch_second", CHARGED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Ride %d is missing or already charged".formatted(stored.id()));
        Map<String, Object> row = jdbc.sql("SELECT stripe_charge_id FROM rides").query().singleRow();
        assertThat(row).containsEntry("stripe_charge_id", "ch_first");
    }

    @Test
    @DisplayName("IT markCharged() when the ride does not exist should throw IllegalStateException")
    void markCharged_whenRideDoesNotExist_shouldThrowIllegalStateException() {
        // when / then
        assertThatThrownBy(() -> tested.markCharged(404L, "ch_123", CHARGED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    private NewRide newRide(UUID key) {
        return new NewRide(userId, key, ORIGIN, TARGET, 2000L, "usd");
    }
}
