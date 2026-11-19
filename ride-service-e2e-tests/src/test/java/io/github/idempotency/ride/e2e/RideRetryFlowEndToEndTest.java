package io.github.idempotency.ride.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.e2e.support.EndToEndTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.idempotency.ride.e2e.support.Waits.awaitAtMost;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A client that gave up waiting for its booking retries it, the way the Idempotency-Key header is meant to be used:
 * with the same key until the answer is definitive.
 */
class RideRetryFlowEndToEndTest extends EndToEndTest {

    private static final String BODY = """
            {"origin": {"lat": 40.7127753, "lon": -74.0059728}, "target": {"lat": 40.6413111, "lon": -73.7781391}}
            """;
    // short enough that the client stops waiting while the booking is still in the server
    private static final Duration IMPATIENT = Duration.ofMillis(1);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("E2E retry when the first call timed out on the client should end with the one ride it booked")
    void retry_whenFirstCallTimedOutOnClient_shouldEndWithTheOneRideItBooked() {
        // given
        String key = UUID.randomUUID().toString();
        client().bookRide(RIDER, key, BODY, IMPATIENT);
        AtomicReference<HttpResponse<String>> definitive = new AtomicReference<>();

        // when
        awaitAtMost().untilAsserted(() -> {
            HttpResponse<String> retry = client().bookRide(RIDER, key, BODY);
            assertThat(retry.statusCode()).as(retry.body()).isIn(201, 409);
            assertThat(retry.statusCode()).as(retry.body()).isEqualTo(201);
            definitive.set(retry);
        });
        HttpResponse<String> again = client().bookRide(RIDER, key, BODY);

        // then
        assertThat(again.statusCode()).as(again.body()).isEqualTo(201);
        assertThat(again.body()).isEqualTo(definitive.get().body());
    }

    @Test
    @DisplayName("E2E retry when it comes under a new key after a timeout should book a second ride")
    void retry_whenItComesUnderNewKeyAfterTimeout_shouldBookSecondRide() throws Exception {
        // given
        String firstKey = UUID.randomUUID().toString();
        client().bookRide(RIDER, firstKey, BODY, IMPATIENT);
        AtomicReference<HttpResponse<String>> first = new AtomicReference<>();
        awaitAtMost().untilAsserted(() -> {
            HttpResponse<String> retry = client().bookRide(RIDER, firstKey, BODY);
            assertThat(retry.statusCode()).as(retry.body()).isEqualTo(201);
            first.set(retry);
        });

        // when
        Optional<HttpResponse<String>> second = client().bookRide(
                RIDER,
                UUID.randomUUID().toString(),
                BODY,
                Duration.ofSeconds(30)
        );

        // then
        assertThat(second).hasValueSatisfying(response -> assertThat(response.statusCode()).isEqualTo(201));
        assertThat(objectMapper.readTree(second.orElseThrow().body()).get("stripeChargeId").asText())
                .isNotEqualTo(objectMapper.readTree(first.get().body()).get("stripeChargeId").asText());
    }
}
