package io.github.idempotency.ride.audit;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Writes the audit trail. A record is written only inside the transaction of the change it describes - never on
 * its own - so the trail and the data it describes are committed or rolled back together.
 */
@Repository
public class AuditRepository {

    private final JdbcClient jdbc;

    public AuditRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    /**
     * @param record the entry to append
     * @throws org.springframework.transaction.IllegalTransactionStateException when no transaction is active
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(AuditRecord record) {
        jdbc.sql("""
                        INSERT INTO audit_records (action, data, origin_ip, resource_type, resource_id, user_id)
                        VALUES (:action, :data::jsonb, :originIp::inet, :resourceType, :resourceId, :userId)
                        """)
                .param("action", record.action().name())
                .param("data", record.data().toString())
                .param("originIp", record.originIp())
                .param("resourceType", record.resourceType().name())
                .param("resourceId", record.resourceId())
                .param("userId", record.userId())
                .update();
    }
}
