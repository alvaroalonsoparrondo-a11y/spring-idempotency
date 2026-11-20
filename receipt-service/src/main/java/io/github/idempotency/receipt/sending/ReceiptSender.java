package io.github.idempotency.receipt.sending;

import io.github.idempotency.contract.LogFields;
import io.github.idempotency.contract.ReceiptJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Sends the receipt of a charged ride to its rider and keeps it. Runs in the transaction the inbox opens for the
 * job, so the receipt and the record of the job commit together.
 */
@Service
public class ReceiptSender {

    private static final Logger log = LoggerFactory.getLogger(ReceiptSender.class);

    private final ReceiptRepository receiptRepository;

    public ReceiptSender(ReceiptRepository receiptRepository) {
        this.receiptRepository = Objects.requireNonNull(receiptRepository, "receiptRepository cannot be null");
    }

    public void send(ReceiptJob job) {
        if (!receiptRepository.saveIfAbsent(job)) {
            log.atInfo()
                    .addKeyValue(LogFields.RIDE_ID, job.rideId())
                    .log("Receipt for ride {} already sent", job.rideId());
            return;
        }
        log.atInfo()
                .addKeyValue(LogFields.RIDE_ID, job.rideId())
                .addKeyValue(LogFields.USER_ID, job.userId())
                .addKeyValue(LogFields.STRIPE_CHARGE_ID, job.stripeChargeId())
                .log("Receipt sent for ride {}: {} {}", job.rideId(), job.amount(), job.currency());
    }
}
