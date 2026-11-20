package io.github.idempotency.receipt.sending;

import io.github.dmitriyiliyov.oncebox.core.OnceboxHeadersUtils;
import io.github.dmitriyiliyov.oncebox.core.inbox.IdempotentConsumer;
import io.github.dmitriyiliyov.oncebox.starter.inbox.TransactionalConsumer;
import io.github.idempotency.contract.ReceiptJob;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Takes receipt jobs off Kafka. A job is redelivered after a crash or a rebalance; the inbox records its event id in
 * the transaction of the receipt, so a job already handled is skipped and a rider gets one receipt per ride.
 */
@Component
public class ReceiptJobListener {

    private final IdempotentConsumer idempotentConsumer;
    private final ReceiptSender receiptSender;

    public ReceiptJobListener(@TransactionalConsumer IdempotentConsumer idempotentConsumer,
                              ReceiptSender receiptSender) {
        this.idempotentConsumer = Objects.requireNonNull(idempotentConsumer, "idempotentConsumer cannot be null");
        this.receiptSender = Objects.requireNonNull(receiptSender, "receiptSender cannot be null");
    }

    @KafkaListener(
            topics = "ride-receipts",
            groupId = "receipt-service",
            containerFactory = "inboxKafkaListenerContainerFactory"
    )
    public void listen(Message<ReceiptJob> message) {
        idempotentConsumer.consume(
                message,
                OnceboxHeadersUtils::extractId,
                received -> receiptSender.send(received.getPayload())
        );
    }
}
