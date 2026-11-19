package io.github.idempotency.ride.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestInfrastructure;
import io.github.idempotency.ride.web.ProblemTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ride endpoint over HTTP on a running server, with idempify, PostgreSQL and stripe-mock behind it.
 */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PostRideIntegrationTest {

    private static final String RIDER = "rider@example.com";
    private static final String BODY = """
            {"origin": {"lat": 52.5200066, "lon": 13.404954}, "target": {"lat": 52.3675734, "lon": 4.9041389}}
            """;

    private final TestRestTemplate rest;
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    PostRideIntegrationTest(TestRestTemplate rest, JdbcClient jdbc, ObjectMapper objectMapper) {
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
        jdbc.sql("TRUNCATE idempotent_operations, audit_records, rides, users RESTART IDENTITY CASCADE").update();
        jdbc.sql("INSERT INTO users (email, stripe_customer_id) VALUES (?, 'cus_rider')").param(RIDER).update();
    }

    @Test
    @DisplayName("IT post() when the key is new should book, audit and charge the ride and answer 201")
    void post_whenKeyIsNew_shouldBookAuditAndChargeRideAndAnswerCreated() throws Exception {
        // when
        ResponseEntity<String> response = post(UUID.randomUUID().toString(), RIDER, BODY);

        // then
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode ride = objectMapper.readTree(response.getBody());
        assertThat(ride.get("stripeChargeId").asText()).startsWith("ch_");
        assertThat(ride.get("amount").asLong()).isEqualTo(2000L);
        Map<String, Object> stored = jdbc.sql("SELECT id, stripe_charge_id FROM rides").query().singleRow();
        assertThat(stored).containsEntry("id", ride.get("id").asLong())
                .containsEntry("stripe_charge_id", ride.get("stripeChargeId").asText());
        assertThat(count("audit_records")).isEqualTo(1L);
    }

    @Test
    @DisplayName("IT post() when repeated under the same key should replay the first answer and book nothing more")
    void post_whenRepeatedUnderSameKey_shouldReplayFirstAnswerAndBookNothingMore() {
        // given
        String key = UUID.randomUUID().toString();
        ResponseEntity<String> first = post(key, RIDER, BODY);

        // when
        ResponseEntity<String> repeat = post(key, RIDER, BODY);

        // then
        assertThat(repeat.getStatusCode()).as(repeat.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeat.getBody()).isEqualTo(first.getBody());
        assertThat(count("rides")).isEqualTo(1L);
        assertThat(count("audit_records")).isEqualTo(1L);
    }

    @Test
    @DisplayName("IT post() when the body is reformatted under the same key should still replay the first answer")
    void post_whenBodyIsReformattedUnderSameKey_shouldStillReplayFirstAnswer() {
        // given
        String key = UUID.randomUUID().toString();
        ResponseEntity<String> first = post(key, RIDER, BODY);
        String reordered = """
                {"target": {"lon": 4.9041389, "lat": 52.3675734},
                 "origin": {"lon": 13.404954, "lat": 52.5200066}}
                """;

        // when
        ResponseEntity<String> repeat = post(key, RIDER, reordered);

        // then
        assertThat(repeat.getStatusCode()).as(repeat.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeat.getBody()).isEqualTo(first.getBody());
        assertThat(count("rides")).isEqualTo(1L);
    }

    @Test
    @DisplayName("IT post() when the same key comes with another body should answer 422 and book nothing more")
    void post_whenSameKeyComesWithAnotherBody_shouldAnswerUnprocessableAndBookNothingMore() throws Exception {
        // given
        String key = UUID.randomUUID().toString();
        post(key, RIDER, BODY);
        String elsewhere = """
                {"origin": {"lat": 52.5200066, "lon": 13.404954}, "target": {"lat": 48.8566, "lon": 2.3522}}
                """;

        // when
        ResponseEntity<String> response = post(key, RIDER, elsewhere);

        // then
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(objectMapper.readTree(response.getBody()).get("type").asText())
                .isEqualTo(io.github.dmitriyiliyov.idempify.http.ProblemTypes.IDEMPOTENCY_KEY_REUSE.toString());
        assertThat(count("rides")).isEqualTo(1L);
    }

    @Test
    @DisplayName("IT post() when the user is unknown should answer 401 and leave no idempotency record")
    void post_whenUserIsUnknown_shouldAnswerUnauthorizedAndLeaveNoIdempotencyRecord() throws Exception {
        // when
        ResponseEntity<String> response = post(UUID.randomUUID().toString(), "stranger@example.com", BODY);

        // then
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(objectMapper.readTree(response.getBody()).get("type").asText())
                .isEqualTo(ProblemTypes.UNAUTHORIZED.toString());
        assertThat(count("idempotent_operations")).isZero();
        assertThat(count("rides")).isZero();
    }

    @Test
    @DisplayName("IT post() when an unknown user repeats a known key should answer 401, not the recorded ride")
    void post_whenUnknownUserRepeatsKnownKey_shouldAnswerUnauthorizedNotRecordedRide() {
        // given
        String key = UUID.randomUUID().toString();
        post(key, RIDER, BODY);

        // when
        ResponseEntity<String> response = post(key, "stranger@example.com", BODY);

        // then
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("IT post() when the key is not a UUID should answer 400 with idempify's problem")
    void post_whenKeyIsNotUuid_shouldAnswerBadRequestWithIdempifyProblem() throws Exception {
        // when
        ResponseEntity<String> response = post("ride-123", RIDER, BODY);

        // then
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).get("type").asText())
                .isEqualTo(io.github.dmitriyiliyov.idempify.http.ProblemTypes.INVALID_IDEMPOTENCY_KEY.toString());
        assertThat(count("rides")).isZero();
    }

    @Test
    @DisplayName("IT post() when the key is missing should answer 400 with idempify's problem")
    void post_whenKeyIsMissing_shouldAnswerBadRequestWithIdempifyProblem() throws Exception {
        // when
        ResponseEntity<String> response = post(null, RIDER, BODY);

        // then
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).get("type").asText())
                .isEqualTo(io.github.dmitriyiliyov.idempify.http.ProblemTypes.INVALID_IDEMPOTENCY_KEY.toString());
    }

    @Test
    @DisplayName("IT post() when the key header is sent twice should book under the first and claim nothing else")
    void post_whenKeyHeaderIsSentTwice_shouldBookUnderFirstAndClaimNothingElse() {
        // given
        String first = UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, RIDER);
        headers.add("Idempotency-Key", first);
        headers.add("Idempotency-Key", UUID.randomUUID().toString());

        // when
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/rides",
                new HttpEntity<>(BODY, headers),
                String.class
        );

        // then
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.sql("SELECT idempotency_key::text FROM rides").query(String.class).single()).isEqualTo(first);
        assertThat(jdbc.sql("SELECT idempotency_key::text FROM idempotent_operations").query(String.class).list())
                .containsExactly(first);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{not json", ""})
    @DisplayName("IT post() when the body is not JSON should answer 400 and claim no operation")
    void post_whenBodyIsNotJson_shouldAnswerBadRequestAndClaimNoOperation(String body) throws Exception {
        // when
        ResponseEntity<String> response = post(UUID.randomUUID().toString(), RIDER, body);

        // then
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objectMapper.readTree(response.getBody()).get("detail").asText())
                .isEqualTo("Failed to read request");
        assertThat(count("idempotent_operations")).isZero();
    }

    @Test
    @DisplayName("IT post() when the body fails validation should answer 400 and leave the key free")
    void post_whenBodyFailsValidation_shouldAnswerBadRequestAndLeaveKeyFree() {
        // given
        String key = UUID.randomUUID().toString();

        // when
        ResponseEntity<String> invalid = post(key, RIDER, "{\"origin\": {\"lat\": 91, \"lon\": 0}}");
        ResponseEntity<String> corrected = post(key, RIDER, BODY);

        // then
        assertThat(invalid.getStatusCode()).as(invalid.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(corrected.getStatusCode()).as(corrected.getBody()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("IT post() when two keys are used should book two rides with two charges")
    void post_whenTwoKeysAreUsed_shouldBookTwoRidesWithTwoCharges() {
        // when
        post(UUID.randomUUID().toString(), RIDER, BODY);
        post(UUID.randomUUID().toString(), RIDER, BODY);

        // then
        assertThat(jdbc.sql("SELECT count(DISTINCT stripe_charge_id) FROM rides").query(Long.class).single())
                .isEqualTo(2L);
    }

    private ResponseEntity<String> post(String key, String user, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, user);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return rest.postForEntity("/api/v1/rides", new HttpEntity<>(body, headers), String.class);
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
