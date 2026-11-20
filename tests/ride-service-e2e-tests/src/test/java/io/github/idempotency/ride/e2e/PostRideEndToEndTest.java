package io.github.idempotency.ride.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.e2e.support.EndToEndTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostRideEndToEndTest extends EndToEndTest {

    private static final String BODY = """
            {"origin": {"lat": 52.5200066, "lon": 13.404954}, "target": {"lat": 52.3675734, "lon": 4.9041389},
             "client": {"appVersion": "4.2.0", "platform": "android"}}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("E2E post() when a rider books a ride should charge it and answer every repeat with the same ride")
    void post_whenRiderBooksRide_shouldChargeItAndAnswerEveryRepeatWithSameRide() throws Exception {
        // given
        String key = UUID.randomUUID().toString();

        // when
        HttpResponse<String> booked = client().bookRide(RIDER, key, BODY);
        HttpResponse<String> repeated = client().bookRide(RIDER, key, BODY);

        // then
        assertThat(booked.statusCode()).as(booked.body()).isEqualTo(201);
        JsonNode ride = objectMapper.readTree(booked.body());
        assertThat(ride.get("stripeChargeId").asText()).startsWith("ch_");
        assertThat(ride.get("amount").asLong()).isEqualTo(2000L);
        assertThat(ride.get("currency").asText()).isEqualTo("usd");
        assertThat(repeated.statusCode()).as(repeated.body()).isEqualTo(201);
        assertThat(repeated.body()).isEqualTo(booked.body());
    }

    @Test
    @DisplayName("E2E post() when two riders book with their own keys should book a ride for each")
    void post_whenTwoRidersBookWithOwnKeys_shouldBookRideForEach() throws Exception {
        // when
        HttpResponse<String> first = client().bookRide(RIDER, UUID.randomUUID().toString(), BODY);
        HttpResponse<String> second = client().bookRide(SECOND_RIDER, UUID.randomUUID().toString(), BODY);

        // then
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        assertThat(second.statusCode()).as(second.body()).isEqualTo(201);
        assertThat(objectMapper.readTree(second.body()).get("id").asLong())
                .isNotEqualTo(objectMapper.readTree(first.body()).get("id").asLong());
    }

    @Test
    @DisplayName("E2E post() when the caller names no known user should answer 401")
    void post_whenCallerNamesNoKnownUser_shouldAnswerUnauthorized() {
        // when
        HttpResponse<String> response = client().bookRide("nobody@example.com", UUID.randomUUID().toString(), BODY);

        // then
        assertThat(response.statusCode()).as(response.body()).isEqualTo(401);
    }

    @Test
    @DisplayName("E2E post() when the caller sends no Authorization header should answer 401")
    void post_whenCallerSendsNoAuthorizationHeader_shouldAnswerUnauthorized() {
        // when
        HttpResponse<String> response = client().bookRide(null, UUID.randomUUID().toString(), BODY);

        // then
        assertThat(response.statusCode()).as(response.body()).isEqualTo(401);
    }

    @Test
    @DisplayName("E2E post() when a key is reused for another route should answer 422 and keep the first ride")
    void post_whenKeyIsReusedForAnotherRoute_shouldAnswerUnprocessableAndKeepFirstRide() throws Exception {
        // given
        String key = UUID.randomUUID().toString();
        HttpResponse<String> booked = client().bookRide(RIDER, key, BODY);
        String elsewhere = """
                {"origin": {"lat": 52.5200066, "lon": 13.404954}, "target": {"lat": 48.8566, "lon": 2.3522}}
                """;

        // when
        HttpResponse<String> reused = client().bookRide(RIDER, key, elsewhere);
        HttpResponse<String> repeated = client().bookRide(RIDER, key, BODY);

        // then
        assertThat(reused.statusCode()).as(reused.body()).isEqualTo(422);
        assertThat(objectMapper.readTree(reused.body()).get("type").asText())
                .isEqualTo("https://idempify.io/errors/idempotency-key-reuse");
        assertThat(repeated.body()).isEqualTo(booked.body());
    }
}
