package io.github.idempotency.ride;

import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestImages;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@RequiresDocker
class SchemaMigrationIntegrationTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TestImages.POSTGRES);

    private static JdbcClient jdbc;

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
    void clean() {
        jdbc.sql("TRUNCATE audit_records, rides, users RESTART IDENTITY CASCADE").update();
    }

    @Test
    @DisplayName("IT migrate() when the database is empty should create the users, rides and audit tables")
    void migrate_whenDatabaseIsEmpty_shouldCreateBusinessTables() {
        // when
        List<String> tables = jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                        ORDER BY table_name
                        """)
                .query(String.class)
                .list();

        // then
        assertThat(tables).containsExactly("audit_records", "rides", "users");
    }

    @Test
    @DisplayName("IT insert() when a second ride takes an idempotency key already used should be refused")
    void insert_whenSecondRideReusesIdempotencyKey_shouldBeRefused() {
        // given
        long userId = givenUser("rider@example.com");
        givenRide(userId, KEY);

        // when / then
        assertThatThrownBy(() -> givenRide(userId, KEY)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM rides").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @DisplayName("IT insert() when a charge id comes without the time it was charged should be refused")
    void insert_whenChargeIdComesWithoutChargedAt_shouldBeRefused() {
        // given
        long userId = givenUser("rider@example.com");

        // when / then
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO rides (user_id, idempotency_key, origin_lat, origin_lon, target_lat, target_lon,
                                           amount, currency, stripe_charge_id)
                        VALUES (?, ?, 0, 0, 0, 0, 2000, 'usd', 'ch_1')
                        """)
                .params(userId, KEY)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("IT insert() when two users share an email should be refused")
    void insert_whenTwoUsersShareEmail_shouldBeRefused() {
        // given
        givenUser("rider@example.com");

        // when / then
        assertThatThrownBy(() -> givenUser("rider@example.com")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("IT insert() when an audit record comes from an IPv6 address should keep the address")
    void insert_whenAuditRecordComesFromIpv6_shouldKeepAddress() {
        // given
        long userId = givenUser("rider@example.com");

        // when
        jdbc.sql("""
                        INSERT INTO audit_records (action, data, origin_ip, resource_type, resource_id, user_id)
                        VALUES ('CREATE_RIDE', '{}'::jsonb, '2001:db8::1'::inet, 'RIDE', 1, ?)
                        """)
                .param(userId)
                .update();

        // then
        assertThat(jdbc.sql("SELECT host(origin_ip) FROM audit_records").query(String.class).single())
                .isEqualTo("2001:db8::1");
    }

    private long givenUser(String email) {
        return jdbc.sql("INSERT INTO users (email, stripe_customer_id) VALUES (?, ?) RETURNING id")
                .params(email, "cus_" + email.hashCode())
                .query(Long.class)
                .single();
    }

    private void givenRide(long userId, UUID key) {
        jdbc.sql("""
                        INSERT INTO rides (user_id, idempotency_key, origin_lat, origin_lon, target_lat, target_lon,
                                           amount, currency)
                        VALUES (?, ?, 0, 0, 0, 0, 2000, 'usd')
                        """)
                .params(userId, key)
                .update();
    }
}
