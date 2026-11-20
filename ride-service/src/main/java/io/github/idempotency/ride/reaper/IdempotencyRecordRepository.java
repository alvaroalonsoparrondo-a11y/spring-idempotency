package io.github.idempotency.ride.reaper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * The application's side of idempify's operations table: idempify writes and reads its rows but never deletes an
 * expired one, so the service does.
 */
@Repository
public class IdempotencyRecordRepository {

    private final JdbcClient jdbc;

    public IdempotencyRecordRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    /**
     * Deletes up to {@code limit} records whose expiry has passed by the database's clock. A record still in process
     * has no expiry and is never selected. Rows another instance is deleting at the same time are skipped rather
     * than waited for.
     *
     * @param limit the most records to delete in this call
     * @return how many were deleted
     */
    int deleteExpired(int limit) {
        return jdbc.sql("""
                        DELETE FROM idempotent_operations
                        WHERE ctid IN (
                            SELECT ctid FROM idempotent_operations
                            WHERE expires_at < now()
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED
                        )
                        """)
                .param("limit", limit)
                .update();
    }
}
