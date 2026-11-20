package io.github.idempotency.ride.reaper;

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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against idempify's own operations table, migrated from the script the library ships.
 */
@Testcontainers
@RequiresDocker
class IdempotencyRecordRepositoryIntegrationTest {

    private static final UUID EXPIRED = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID LIVE = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000002");
    private static final UUID IN_PROCESS = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000003");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TestImages.POSTGRES);

    private static JdbcClient jdbc;

    private final IdempotencyRecordRepository tested = new IdempotencyRecordRepository(jdbc);

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        Flyway.configure().dataSource(dataSource).locations("classpath:db/idempify/postgresql").load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE idempotent_operations").update();
    }

    @Test
    @DisplayName("IT deleteExpired() should delete the expired record and keep the live one and the one in process")
    void deleteExpired_whenRecordsOfEveryKindExist_shouldDeleteOnlyExpiredOne() {
        // given
        givenRecord(EXPIRED, "PROCESSED", "now() - interval '1 second'");
        givenRecord(LIVE, "PROCESSED", "now() + interval '1 hour'");
        givenRecord(IN_PROCESS, "IN_PROCESS", "NULL");

        // when
        int deleted = tested.deleteExpired(100);

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(keys()).containsExactlyInAnyOrder(LIVE, IN_PROCESS);
    }

    @Test
    @DisplayName("IT deleteExpired() when a failure kept on record has expired should delete it too")
    void deleteExpired_whenFailureKeptOnRecordHasExpired_shouldDeleteItToo() {
        // given
        givenRecord(EXPIRED, "EXCEPTIONALLY_PROCESSED", "now() - interval '1 minute'");

        // when
        int deleted = tested.deleteExpired(100);

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(keys()).isEmpty();
    }

    @Test
    @DisplayName("IT deleteExpired() when more records expired than the limit should delete exactly the limit")
    void deleteExpired_whenMoreRecordsExpiredThanLimit_shouldDeleteExactlyLimit() {
        // given
        for (int i = 0; i < 5; i++) {
            givenRecord(UUID.randomUUID(), "PROCESSED", "now() - interval '1 hour'");
        }

        // when
        int deleted = tested.deleteExpired(3);

        // then
        assertThat(deleted).isEqualTo(3);
        assertThat(keys()).hasSize(2);
    }

    @Test
    @DisplayName("IT deleteExpired() when the table is empty should delete nothing")
    void deleteExpired_whenTableIsEmpty_shouldDeleteNothing() {
        // when
        int deleted = tested.deleteExpired(100);

        // then
        assertThat(deleted).isZero();
    }

    private static void givenRecord(UUID key, String status, String expiresAt) {
        jdbc.sql("""
                        INSERT INTO idempotent_operations (idempotency_key, status, is_first_attempt, expires_at)
                        VALUES (?, ?, true, %s)
                        """.formatted(expiresAt))
                .params(key, status)
                .update();
    }

    private static List<UUID> keys() {
        return jdbc.sql("SELECT idempotency_key FROM idempotent_operations").query(UUID.class).list();
    }
}
