package io.github.idempotency.ride.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestInfrastructure;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.idempotency.ride.support.Waits.awaitAtMost;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A booked ride's receipt job, from the outbox to the Kafka topic the receipt service reads.
 */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ReceiptJobPublishingIntegrationTest {

    private static final String RIDER = "rider@example.com";
    private static final String BODY = """
            {"origin": {"lat": 52.5200066, "lon": 13.404954}, "target": {"lat": 52.3675734, "lon": 4.9041389}}
            """;

    private final TestRestTemplate rest;
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private KafkaConsumer<String, String> consumer;

    ReceiptJobPublishingIntegrationTest(TestRestTemplate rest, JdbcClient jdbc, ObjectMapper objectMapper) {
        this.rest = rest;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry);
    }

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE outbox_events, idempotent_operations, audit_records, rides, users RESTART IDENTITY CASCADE")
                .update();
        jdbc.sql("INSERT INTO users (email, stripe_customer_id) VALUES (?, 'cus_rider')").param(RIDER).update();
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestInfrastructure.kafkaBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "receipt-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
        consumer.subscribe(List.of("ride-receipts"));
        awaitAtMost().until(() -> {
            consumer.poll(Duration.ofMillis(100));
            return !consumer.assignment().isEmpty();
        });
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    @DisplayName("IT publish when a ride is charged should deliver its receipt job with the oncebox headers")
    void publish_whenRideIsCharged_shouldDeliverReceiptJobWithOnceboxHeaders() throws Exception {
        // given
        ResponseEntity<String> booked = post(UUID.randomUUID());
        JsonNode ride = objectMapper.readTree(booked.getBody());
        String eventId = jdbc.sql("SELECT id::text FROM outbox_events").query(String.class).single();
        List<ConsumerRecord<String, String>> received = new ArrayList<>();

        // when
        awaitAtMost().untilAsserted(() -> {
            consumer.poll(Duration.ofMillis(200)).forEach(received::add);
            assertThat(received).hasSize(1);
        });

        // then
        ConsumerRecord<String, String> record = received.getFirst();
        assertThat(header(record, "oncebox_event_type")).isEqualTo("ride-receipt");
        assertThat(header(record, "oncebox_event_id")).isEqualTo(eventId);
        JsonNode job = objectMapper.readTree(record.value());
        assertThat(job.get("rideId").asLong()).isEqualTo(ride.get("id").asLong());
        assertThat(job.get("stripeChargeId").asText()).isEqualTo(ride.get("stripeChargeId").asText());
        awaitAtMost().untilAsserted(() -> assertThat(
                jdbc.sql("SELECT status FROM outbox_events").query(String.class).single()
        ).isEqualTo("PROCESSED"));
    }

    @Test
    @DisplayName("IT publish when a booking is repeated under its key should deliver one receipt job")
    void publish_whenBookingIsRepeatedUnderItsKey_shouldDeliverOneReceiptJob() {
        // given
        UUID key = UUID.randomUUID();
        post(key);
        post(key);
        List<ConsumerRecord<String, String>> received = new ArrayList<>();

        // when
        awaitAtMost().untilAsserted(() -> assertThat(
                jdbc.sql("SELECT status FROM outbox_events").query(String.class).list()
        ).containsExactly("PROCESSED"));
        consumer.poll(Duration.ofSeconds(2)).forEach(received::add);

        // then
        assertThat(received).hasSize(1);
    }

    private ResponseEntity<String> post(UUID key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, RIDER);
        headers.set("Idempotency-Key", key.toString());
        return rest.postForEntity("/api/v1/rides", new HttpEntity<>(BODY, headers), String.class);
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
