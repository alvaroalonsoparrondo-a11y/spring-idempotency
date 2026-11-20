package io.github.idempotency.receipt.sending;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.receipt.support.ReceiptJobs;
import io.github.idempotency.receipt.support.ReceiptServiceIntegrationTest;
import io.github.idempotency.receipt.support.TestInfrastructure;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.idempotency.receipt.support.Waits.awaitAtMost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Receipt jobs that fail on the way, consumed from a real Kafka into a real PostgreSQL.
 */
class ReceiptJobConsumptionIntegrationTest extends ReceiptServiceIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private KafkaConsumer<String, String> deadLetters;

    @BeforeEach
    void subscribeToDeadLetters() {
        deadLetters = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestInfrastructure.kafkaBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dead-letters-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, true
        ));
        deadLetters.subscribe(List.of("ride-receipts-dlt"));
        awaitAtMost().until(() -> {
            deadLetters.poll(Duration.ofMillis(100));
            return !deadLetters.assignment().isEmpty();
        });
    }

    @AfterEach
    void closeDeadLetters() {
        deadLetters.close();
    }

    @Test
    @DisplayName("IT listen() when storing the receipt fails once should roll the job back and send it on redelivery")
    void listen_whenStoringReceiptFailsOnce_shouldRollJobBackAndSendItOnRedelivery() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        doThrow(new DataAccessResourceFailureException("connection reset"))
                .doCallRealMethod()
                .when(receiptRepository).saveIfAbsent(ReceiptJobs.job(9L));

        // when
        ReceiptJobs.publish(eventId, objectMapper.writeValueAsString(ReceiptJobs.job(9L)));

        // then
        awaitAtMost().untilAsserted(() -> assertThat(
                jdbc.sql("SELECT ride_id FROM receipts").query(Long.class).list()
        ).containsExactly(9L));
        assertThat(jdbc.sql("SELECT id FROM inbox_events").query(UUID.class).list()).containsExactly(eventId);
        verify(receiptSender, atLeast(2)).send(ReceiptJobs.job(9L));
    }

    @Test
    @DisplayName("IT listen() when storing the receipt keeps failing should move the job to the dead letter topic")
    void listen_whenStoringReceiptKeepsFailing_shouldMoveJobToDeadLetterTopic() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        doThrow(new DataAccessResourceFailureException("database down"))
                .when(receiptRepository).saveIfAbsent(ReceiptJobs.job(10L));

        // when
        ReceiptJobs.publish(eventId, objectMapper.writeValueAsString(ReceiptJobs.job(10L)));

        // then
        ConsumerRecord<String, String> deadLetter = awaitDeadLetter();
        assertThat(header(deadLetter, "oncebox_event_id")).isEqualTo(eventId.toString());
        assertThat(objectMapper.readTree(deadLetter.value()).get("rideId").asLong()).isEqualTo(10L);
        assertThat(jdbc.sql("SELECT count(*) FROM receipts").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM inbox_events").query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("IT listen() when the payload is not a receipt job should move it to the dead letter topic")
    void listen_whenPayloadIsNotReceiptJob_shouldMoveItToDeadLetterTopic() throws Exception {
        // when
        ReceiptJobs.publish(UUID.randomUUID(), "{not json");

        // then
        ConsumerRecord<String, String> deadLetter = awaitDeadLetter();
        assertThat(deadLetter.value()).isEqualTo("{not json");
        verify(receiptSender, never()).send(any());
    }

    private ConsumerRecord<String, String> awaitDeadLetter() {
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        awaitAtMost().untilAsserted(() -> {
            deadLetters.poll(Duration.ofMillis(200)).forEach(received::add);
            assertThat(received).hasSize(1);
        });
        return received.getFirst();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
