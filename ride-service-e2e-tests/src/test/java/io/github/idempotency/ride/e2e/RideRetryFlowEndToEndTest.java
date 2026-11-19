package io.github.idempotency.ride.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.e2e.support.EndToEndTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.idempotency.ride.e2e.support.Waits.awaitAtMost;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A client that gave up waiting for its booking retries it, the way the Idempotency-Key header is meant to be used:
 * with the same key until the answer is definitive. The first request always reaches the server in full; the client
 * hangs up before the answer.
 */
class RideRetryFlowEndToEndTest extends EndToEndTest {

    private static final String BODY = """
            {"origin": {"lat": 40.7127753, "lon": -74.0059728}, "target": {"lat": 40.6413111, "lon": -73.7781391}}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("E2E retry when the client hung up on the first call should settle on one answer for the key")
    void retry_whenClientHungUpOnFirstCall_shouldSettleOnOneAnswerForKey() throws Exception {
        // given
        String key = UUID.randomUUID().toString();
        client().bookRideAndHangUp(RIDER, key, BODY);

        // when
        HttpResponse<String> settled = retryUntilBooked(key);
        HttpResponse<String> again = client().bookRide(RIDER, key, BODY);

        // then
        assertThat(objectMapper.readTree(settled.body()).get("stripeChargeId").asText()).startsWith("ch_");
        assertThat(again.statusCode()).as(again.body()).isEqualTo(201);
        assertThat(again.body()).isEqualTo(settled.body());
    }

    @Test
    @DisplayName("E2E retry when it comes under a new key after a hang-up should book a second ride")
    void retry_whenItComesUnderNewKeyAfterHangUp_shouldBookSecondRide() throws Exception {
        // given
        String firstKey = UUID.randomUUID().toString();
        client().bookRideAndHangUp(RIDER, firstKey, BODY);
        HttpResponse<String> first = retryUntilBooked(firstKey);

        // when
        HttpResponse<String> second = client().bookRide(RIDER, UUID.randomUUID().toString(), BODY);

        // then
        assertThat(second.statusCode()).as(second.body()).isEqualTo(201);
        assertThat(objectMapper.readTree(second.body()).get("id").asLong())
                .isNotEqualTo(objectMapper.readTree(first.body()).get("id").asLong());
        assertThat(objectMapper.readTree(second.body()).get("stripeChargeId").asText())
                .isNotEqualTo(objectMapper.readTree(first.body()).get("stripeChargeId").asText());
    }

    /**
     * Retries the way a client should: an answer that the booking is still in flight means try again.
     */
    private HttpResponse<String> retryUntilBooked(String key) {
        AtomicReference<HttpResponse<String>> booked = new AtomicReference<>();
        awaitAtMost().untilAsserted(() -> {
            HttpResponse<String> retry = client().bookRide(RIDER, key, BODY);
            assertThat(retry.statusCode()).as(retry.body()).isIn(201, 409);
            assertThat(retry.statusCode()).as(retry.body()).isEqualTo(201);
            booked.set(retry);
        });
        return booked.get();
    }
}
