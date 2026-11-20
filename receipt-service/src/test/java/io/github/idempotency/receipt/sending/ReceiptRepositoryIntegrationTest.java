package io.github.idempotency.receipt.sending;

import io.github.idempotency.contract.ReceiptJob;
import io.github.idempotency.receipt.support.ReceiptJobs;
import io.github.idempotency.receipt.support.RequiresDocker;
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
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@RequiresDocker
class ReceiptRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17.12-alpine")
    );

    private static JdbcClient jdbc;

    private final ReceiptRepository tested = new ReceiptRepository(jdbc);

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
        jdbc.sql("TRUNCATE receipts RESTART IDENTITY").update();
    }

    @Test
    @DisplayName("IT saveIfAbsent() when the ride has no receipt should store the job's receipt")
    void saveIfAbsent_whenRideHasNoReceipt_shouldStoreJobReceipt() {
        // given
        ReceiptJob job = ReceiptJobs.job(7L);

        // when
        boolean stored = tested.saveIfAbsent(job);

        // then
        assertThat(stored).isTrue();
        Map<String, Object> row = jdbc.sql("SELECT * FROM receipts").query().singleRow();
        assertThat(row).containsEntry("ride_id", 7L)
                .containsEntry("user_id", 3L)
                .containsEntry("stripe_charge_id", "ch_7")
                .containsEntry("amount", 2000L)
                .containsEntry("currency", "usd");
        assertThat(((Timestamp) row.get("charged_at")).toInstant()).isEqualTo(job.chargedAt());
        assertThat(row.get("sent_at")).isNotNull();
    }

    @Test
    @DisplayName("IT saveIfAbsent() when the ride already has a receipt should store nothing")
    void saveIfAbsent_whenRideAlreadyHasReceipt_shouldStoreNothing() {
        // given
        tested.saveIfAbsent(ReceiptJobs.job(7L));

        // when
        boolean stored = tested.saveIfAbsent(ReceiptJobs.job(7L));

        // then
        assertThat(stored).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM receipts").query(Long.class).single()).isEqualTo(1L);
    }
}
