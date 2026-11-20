package io.github.idempotency.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.receipt.support.ReceiptJobs;
import io.github.idempotency.receipt.support.ReceiptServiceIntegrationTest;
import io.github.idempotency.receipt.support.TestInfrastructure;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.idempotency.receipt.support.Waits.awaitAtMost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class ReceiptServiceApplicationIntegrationTest extends ReceiptServiceIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("IT listen() when a job is delivered twice under one event id should send one receipt")
    void listen_whenJobIsDeliveredTwiceUnderOneEventId_shouldSendOneReceipt() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(ReceiptJobs.job(7L));
        // the same partition for all three, so once the marker is handled both deliveries before it are too
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

    @Test
    @DisplayName("IT listen() should log the sent receipt as JSON with the ride and the charge as fields")
    void listen_whenReceiptIsSent_shouldLogItAsJsonWithRideAndChargeAsFields(CapturedOutput output) throws Exception {
        // when
        ReceiptJobs.publish(UUID.randomUUID(), objectMapper.writeValueAsString(ReceiptJobs.job(11L)));

        // then
        awaitAtMost().untilAsserted(() -> assertThat(output.getOut())
                .containsPattern("\\{\"@timestamp\":[^\\n]*\"message\":\"Receipt sent for ride 11: 2000 usd\"")
                .contains("\"rideId\":11")
                .contains("\"stripeChargeId\":\"ch_11\""));
    }

    @Test
    @DisplayName("IT context when started should create the receipt topic and its dead letter topic with their partitions")
    void context_whenStarted_shouldCreateReceiptTopicAndDeadLetterTopicWithPartitions() throws Exception {
        // when
        Map<String, TopicDescription> topics;
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, TestInfrastructure.kafkaBootstrapServers()
        ))) {
            topics = admin.describeTopics(List.of("ride-receipts", "ride-receipts-dlt")).allTopicNames().get();
        }

        // then
        assertThat(topics.get("ride-receipts").partitions()).hasSize(3);
        assertThat(topics.get("ride-receipts-dlt").partitions()).hasSize(3);
    }
}
