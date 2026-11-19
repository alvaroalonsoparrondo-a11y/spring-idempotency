package io.github.idempotency.ride.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestInfrastructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@RequiresDocker
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditRepositoryIntegrationTest {

    private final AuditRepository tested;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    AuditRepositoryIntegrationTest(AuditRepository tested,
                                   JdbcClient jdbc,
                                   TransactionTemplate transaction,
                                   ObjectMapper objectMapper) {
        this.tested = tested;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.objectMapper = objectMapper;
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE audit_records, rides, users RESTART IDENTITY CASCADE").update();
    }

    @Test
    @DisplayName("IT save() when inside a transaction should store the record with its data and origin")
    void save_whenInsideTransaction_shouldStoreRecordWithDataAndOrigin() throws Exception {
        // given
        long userId = givenUser();
        JsonNode data = objectMapper.readTree("{\"origin\":{\"lat\":52.52,\"lon\":13.405}}");

        // when
        transaction.executeWithoutResult(status -> tested.save(new AuditRecord(
                AuditAction.CREATE_RIDE,
                AuditResourceType.RIDE,
                42L,
                userId,
                "2001:db8::1",
                data
        )));

        // then
        Map<String, Object> row = jdbc.sql("""
                        SELECT action, data::text AS data, host(origin_ip) AS origin_ip, resource_type, resource_id,
                               user_id
                        FROM audit_records
                        """)
                .query()
                .singleRow();
        assertThat(row).containsEntry("action", "CREATE_RIDE")
                .containsEntry("origin_ip", "2001:db8::1")
                .containsEntry("resource_type", "RIDE")
                .containsEntry("resource_id", 42L)
                .containsEntry("user_id", userId);
        assertThat(objectMapper.readTree((String) row.get("data"))).isEqualTo(data);
    }

    @Test
    @DisplayName("IT save() when the transaction rolls back should leave no record")
    void save_whenTransactionRollsBack_shouldLeaveNoRecord() {
        // given
        long userId = givenUser();

        // when
        transaction.executeWithoutResult(status -> {
            tested.save(record(userId));
            status.setRollbackOnly();
        });

        // then
        assertThat(jdbc.sql("SELECT count(*) FROM audit_records").query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("IT save() when no transaction is active should be refused and store nothing")
    void save_whenNoTransactionIsActive_shouldBeRefusedAndStoreNothing() {
        // given
        long userId = givenUser();

        // when / then
        assertThatThrownBy(() -> tested.save(record(userId))).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_records").query(Long.class).single()).isZero();
    }

    private long givenUser() {
        return jdbc.sql("INSERT INTO users (email, stripe_customer_id) VALUES ('rider@example.com', 'cus_rider') "
                        + "RETURNING id")
                .query(Long.class)
                .single();
    }

    private AuditRecord record(long userId) {
        return new AuditRecord(
                AuditAction.CREATE_RIDE,
                AuditResourceType.RIDE,
                42L,
                userId,
                "203.0.113.7",
                objectMapper.createObjectNode()
        );
    }
}
