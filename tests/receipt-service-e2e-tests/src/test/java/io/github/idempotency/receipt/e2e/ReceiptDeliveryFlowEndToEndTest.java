package io.github.idempotency.receipt.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.receipt.e2e.support.ReceiptEndToEndTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A booked ride ends with a receipt sent to its rider by the other service.
 */
class ReceiptDeliveryFlowEndToEndTest extends ReceiptEndToEndTest {

    private static final String BODY = """
            {"origin": {"lat": 51.5072178, "lon": -0.1275862}, "target": {"lat": 51.4700223, "lon": -0.4542955}}
            """;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("E2E booking when a ride is charged should end with its receipt sent by the receipt service")
    void booking_whenRideIsCharged_shouldEndWithReceiptSentByReceiptService() throws Exception {
        // when
        JsonNode ride = book(UUID.randomUUID());

        // then
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(receiptOf(ride.get("id").asLong()))
                        .containsEntry("stripe_charge_id", ride.get("stripeChargeId").asText())
                        .containsEntry("amount", 2000L)
                        .containsEntry("currency", "usd"));
    }

    @Test
    @DisplayName("E2E booking when it is repeated under its key should still end with one receipt")
    void booking_whenRepeatedUnderItsKey_shouldStillEndWithOneReceipt() throws Exception {
        // given
        UUID key = UUID.randomUUID();
        long rideId = book(key).get("id").asLong();

        // when
        book(key);
        book(key);

        // then
        await().atMost(Duration.ofSeconds(60)).until(() -> countReceipts(rideId) == 1L);
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(10))
                .until(() -> countReceipts(rideId) == 1L);
    }

    private JsonNode book(UUID key) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/rides"))
                .header("Content-Type", "application/json")
                .header("Authorization", RIDER)
                .header("Idempotency-Key", key.toString())
                .POST(HttpRequest.BodyPublishers.ofString(BODY))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return objectMapper.readTree(response.body());
    }

    private static Map<String, Object> receiptOf(long rideId) {
        return receipts().sql("SELECT * FROM receipts WHERE ride_id = ?").param(rideId).query().singleRow();
    }

    private static long countReceipts(long rideId) {
        return receipts().sql("SELECT count(*) FROM receipts WHERE ride_id = ?")
                .param(rideId)
                .query(Long.class)
                .single();
    }
}
