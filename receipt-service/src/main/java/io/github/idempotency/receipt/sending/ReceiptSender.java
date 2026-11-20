package io.github.idempotency.receipt.sending;

import io.github.idempotency.contract.LogFields;
import io.github.idempotency.contract.ReceiptJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends the receipt of a charged ride to its rider.
 */
@Service
public class ReceiptSender {

    private static final Logger log = LoggerFactory.getLogger(ReceiptSender.class);

    public void send(ReceiptJob job) {
        log.atInfo()
                .addKeyValue(LogFields.RIDE_ID, job.rideId())
                .addKeyValue(LogFields.USER_ID, job.userId())
                .addKeyValue(LogFields.STRIPE_CHARGE_ID, job.stripeChargeId())
                .log("Receipt sent for ride {}: {} {}", job.rideId(), job.amount(), job.currency());
    }
}
