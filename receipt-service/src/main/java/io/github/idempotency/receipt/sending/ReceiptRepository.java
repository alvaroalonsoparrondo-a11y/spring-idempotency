package io.github.idempotency.receipt.sending;

import io.github.idempotency.contract.ReceiptJob;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Objects;

@Repository
public class ReceiptRepository {

    private final JdbcClient jdbc;

    public ReceiptRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    /**
     * Stores the receipt of the job's ride unless the ride already has one: the inbox skips a redelivered job, and
     * the ride's unique receipt catches a second job for the same ride as well.
     *
     * @return whether the receipt was stored by this call
     */
    boolean saveIfAbsent(ReceiptJob job) {
        return jdbc.sql("""
                        INSERT INTO receipts (ride_id, user_id, stripe_charge_id, amount, currency, charged_at)
                        VALUES (:rideId, :userId, :chargeId, :amount, :currency, :chargedAt)
                        ON CONFLICT (ride_id) DO NOTHING
                        """)
                .param("rideId", job.rideId())
                .param("userId", job.userId())
                .param("chargeId", job.stripeChargeId())
                .param("amount", job.amount())
                .param("currency", job.currency())
                .param("chargedAt", Timestamp.from(job.chargedAt()))
                .update() == 1;
    }
}
