package io.github.idempotency.ride.booking;

import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestInfrastructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Redis cache in front of the recorded responses, on a running server with real Redis and PostgreSQL.
 */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RedisResponseCacheIntegrationTest {

    private static final String RIDER = "rider@example.com";
    private static final String BODY = """
            {"origin": {"lat": 52.5200066, "lon": 13.404954}, "target": {"lat": 52.3675734, "lon": 4.9041389}}
            """;

    private final TestRestTemplate rest;
    private final JdbcClient jdbc;
    private final StringRedisTemplate redis;

    RedisResponseCacheIntegrationTest(TestRestTemplate rest, JdbcClient jdbc, StringRedisTemplate redis) {
        this.rest = rest;
        this.jdbc = jdbc;
        this.redis = redis;
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry);
    }

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE idempotent_operations, audit_records, rides, users RESTART IDENTITY CASCADE").update();
        jdbc.sql("INSERT INTO users (email, stripe_customer_id) VALUES (?, 'cus_rider')").param(RIDER).update();
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("IT post() when a booking completes should cache its answer for no longer than the record lives")
    void post_whenBookingCompletes_shouldCacheAnswerForNoLongerThanRecordLives() {
        // given
        UUID key = UUID.randomUUID();

        // when
        post(key);

        // then
        assertThat(redis.hasKey(cacheKey(key))).isTrue();
        Long ttl = redis.getExpire(cacheKey(key), TimeUnit.SECONDS);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(Duration.ofHours(24).toSeconds());
    }

    @Test
    @DisplayName("IT post() when the answer is cached should replay it without reaching the store")
    void post_whenAnswerIsCached_shouldReplayItWithoutReachingStore() {
        // given
        UUID key = UUID.randomUUID();
        ResponseEntity<String> first = post(key);
        // with the record and the ride gone, only the cache can still answer with the first ride
        jdbc.sql("TRUNCATE idempotent_operations, audit_records, rides RESTART IDENTITY CASCADE").update();
        jdbc.sql("SELECT setval('rides_id_seq', 100)").query(Long.class).single();

        // when
        ResponseEntity<String> repeat = post(key);

        // then
        assertThat(repeat.getStatusCode()).as(repeat.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeat.getBody()).isEqualTo(first.getBody());
        assertThat(jdbc.sql("SELECT count(*) FROM rides").query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("IT post() when the cache lost the answer should replay it from the store and cache it again")
    void post_whenCacheLostAnswer_shouldReplayItFromStoreAndCacheItAgain() {
        // given
        UUID key = UUID.randomUUID();
        ResponseEntity<String> first = post(key);
        redis.delete(cacheKey(key));

        // when
        ResponseEntity<String> repeat = post(key);

        // then
        assertThat(repeat.getStatusCode()).as(repeat.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeat.getBody()).isEqualTo(first.getBody());
        assertThat(redis.hasKey(cacheKey(key))).isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM rides").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @DisplayName("IT post() when a request fails validation should cache nothing")
    void post_whenRequestFailsValidation_shouldCacheNothing() {
        // given
        UUID key = UUID.randomUUID();
        HttpHeaders headers = headers(key);

        // when
        rest.postForEntity("/api/v1/rides", new HttpEntity<>("{\"origin\": {\"lat\": 91, \"lon\": 0}}", headers),
                String.class);

        // then
        assertThat(redis.hasKey(cacheKey(key))).isFalse();
    }

    private ResponseEntity<String> post(UUID key) {
        return rest.postForEntity("/api/v1/rides", new HttpEntity<>(BODY, headers(key)), String.class);
    }

    private static HttpHeaders headers(UUID key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, RIDER);
        headers.set("Idempotency-Key", key.toString());
        return headers;
    }

    private static String cacheKey(UUID key) {
        return "ride-service:" + key;
    }
}
