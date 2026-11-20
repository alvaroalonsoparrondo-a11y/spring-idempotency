package io.github.idempotency.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.receipt.support.ReceiptJobs;
import io.github.idempotency.receipt.support.ReceiptServiceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.github.idempotency.receipt.support.Waits.awaitAtMost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ReceiptServiceApplicationIntegrationTest extends ReceiptServiceIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("IT listen() when a job is delivered twice under one event id should send one receipt")
    void listen_whenJobIsDeliveredTwiceUnderOneEventId_shouldSendOneReceipt() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(ReceiptJobs.job(7L));
        // the topic has one partition, so once the marker is handled both deliveries before it are too
        UUID marker = UUID.randomUUID();

        // when
        ReceiptJobs.publish(eventId, payload);
        ReceiptJobs.publish(eventId, payload);
        ReceiptJobs.publish(marker, objectMapper.writeValueAsString(ReceiptJobs.job(8L)));

        // then
        awaitAtMost().untilAsserted(() -> assertThat(
                jdbc.sql("SELECT id FROM inbox_events").query(UUID.class).list()
        ).containsExactlyInAnyOrder(eventId, marker));
        verify(receiptSender, times(1)).send(ReceiptJobs.job(7L));
        verify(receiptSender, times(2)).send(any());
        assertThat(jdbc.sql("SELECT ride_id FROM receipts").query(Long.class).list()).containsExactlyInAnyOrder(7L, 8L);
    }
}
