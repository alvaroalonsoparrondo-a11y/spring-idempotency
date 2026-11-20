package io.github.idempotency.receipt.support;

import io.github.idempotency.contract.ReceiptJob;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publishes receipt jobs the way ride-service's outbox does: the JSON payload with the oncebox headers.
 */
public final class ReceiptJobs {

    private ReceiptJobs() {}

    public static ReceiptJob job(long rideId) {
        return new ReceiptJob(rideId, 3L, "ch_" + rideId, 2000L, "usd", Instant.parse("2026-11-18T10:15:30Z"));
    }

    public static void publish(UUID eventId, String payload) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, TestInfrastructure.kafkaBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        ))) {
            ProducerRecord<String, String> record = new ProducerRecord<>("ride-receipts", payload);
            record.headers().add("oncebox_event_id", eventId.toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("oncebox_event_type", ReceiptJob.EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
            record.headers().add(
                    "oncebox_event_payload_type",
                    ReceiptJob.class.getName().getBytes(StandardCharsets.UTF_8)
            );
            producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }
}
