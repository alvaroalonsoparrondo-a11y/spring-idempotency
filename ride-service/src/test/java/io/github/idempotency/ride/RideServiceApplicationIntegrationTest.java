package io.github.idempotency.ride;

import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.SharedPostgres;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RequiresDocker
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RideServiceApplicationIntegrationTest {

    private final JdbcClient jdbc;

    RideServiceApplicationIntegrationTest(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry);
    }

    @Test
    @DisplayName("IT context when started should migrate the idempotency table beside the business tables")
    void context_whenStarted_shouldMigrateIdempotencyTableBesideBusinessTables() {
        // when
        Long tables = jdbc.sql("""
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('users', 'rides', 'audit_records', 'idempotent_operations',
                                             'idempify_schema_history', 'flyway_schema_history')
                        """)
                .query(Long.class)
                .single();

        // then
        assertThat(tables).isEqualTo(6L);
    }
}
