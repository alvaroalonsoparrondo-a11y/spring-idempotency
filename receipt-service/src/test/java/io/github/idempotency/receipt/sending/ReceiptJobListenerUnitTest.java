package io.github.idempotency.receipt.sending;

import io.github.idempotency.contract.ReceiptJob;
import io.github.idempotency.receipt.support.ReceiptJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReceiptJobListenerUnitTest {

    private static final UUID EVENT_ID = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");

    private final RunningIdempotentConsumer idempotentConsumer = new RunningIdempotentConsumer();

    @Mock
    private ReceiptSender receiptSender;

    @Test
    @DisplayName("UT listen() should hand the job to the sender under the event id from the oncebox header")
    void listen_whenJobArrives_shouldHandJobToSenderUnderEventIdFromOnceboxHeader() {
        // given
        ReceiptJobListener tested = new ReceiptJobListener(idempotentConsumer, receiptSender);
        ReceiptJob job = ReceiptJobs.job(7L);
        Message<ReceiptJob> message = MessageBuilder.withPayload(job)
                .setHeader("oncebox_event_id", EVENT_ID.toString())
                .build();

        // when
        tested.listen(message);

        // then
        assertThat(idempotentConsumer.consumedIds()).containsExactly(EVENT_ID);
        verify(receiptSender).send(job);
    }

    @Test
    @DisplayName("UT constructor when idempotentConsumer is null should throw NullPointerException")
    void constructor_whenIdempotentConsumerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ReceiptJobListener(null, receiptSender))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("idempotentConsumer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when receiptSender is null should throw NullPointerException")
    void constructor_whenReceiptSenderIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ReceiptJobListener(idempotentConsumer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("receiptSender cannot be null");
    }
}
