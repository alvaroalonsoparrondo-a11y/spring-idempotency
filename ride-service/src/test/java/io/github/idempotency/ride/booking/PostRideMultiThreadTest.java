package io.github.idempotency.ride.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.ride.stripe.StripeChargeClient;
import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestInfrastructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Many clients send the same booking under one key at the same moment, through the running server.
 */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PostRideMultiThreadTest {

    private static final int PARTIES = 10;
    private static final String RIDER = "rider@example.com";
    private static final String BODY = """
            {"origin": {"lat": 52.5200066, "lon": 13.404954}, "target": {"lat": 52.3675734, "lon": 4.9041389}}
            """;
    private static final String IN_PROCESS =
            io.github.dmitriyiliyov.idempify.http.ProblemTypes.OPERATION_IN_PROCESS.toString();

    private final TestRestTemplate rest;
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    // a spy, not a stub: every charge still reaches stripe-mock, the spy only counts them
    @MockitoSpyBean
    private StripeChargeClient stripe;

    PostRideMultiThreadTest(TestRestTemplate rest, JdbcClient jdbc, ObjectMapper objectMapper) {
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
        clearInvocations(stripe);
    }

    @RepeatedTest(20)
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT post() when duplicates arrive at once should charge once and answer the rest in flight or replayed")
    void post_whenDuplicatesArriveAtOnce_shouldChargeOnceAndAnswerRestInFlightOrReplayed() throws Exception {
        // given
        String key = UUID.randomUUID().toString();
        CountDownLatch ready = new CountDownLatch(PARTIES);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        AtomicInteger names = new AtomicInteger();

        // when
        try (ExecutorService pool = Executors.newFixedThreadPool(
                PARTIES,
                runnable -> new Thread(runnable, "duplicate-" + names.incrementAndGet())
        )) {
            for (int i = 0; i < PARTIES; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(15, TimeUnit.SECONDS)).as("start gate opened").isTrue();
                    return post(key);
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).as("every party reached the gate").isTrue();
            start.countDown();
        }

        // then
        List<ResponseEntity<String>> responses = new ArrayList<>();
        for (Future<ResponseEntity<String>> future : futures) {
            responses.add(future.get(15, TimeUnit.SECONDS));
        }
        List<String> created = responses.stream()
                .filter(response -> response.getStatusCode() == HttpStatus.CREATED)
                .map(ResponseEntity::getBody)
                .toList();
        List<ResponseEntity<String>> refused = responses.stream()
                .filter(response -> response.getStatusCode() != HttpStatus.CREATED)
                .toList();
        assertThat(created).as("at least the winner answered 201").isNotEmpty();
        assertThat(created).as("every 201 is the same ride").containsOnly(created.getFirst());
        for (ResponseEntity<String> response : refused) {
            assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(objectMapper.readTree(response.getBody()).get("type").asText())
                    .as(response.getBody())
                    .isEqualTo(IN_PROCESS);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM rides").query(Long.class).single()).as("rides booked").isEqualTo(1L);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_records").query(Long.class).single())
                .as("audit records")
                .isEqualTo(1L);
        verify(stripe, times(1)).charge(anyString(), anyLong(), anyString(), anyString(), eq(UUID.fromString(key)));
    }

    private ResponseEntity<String> post(String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, RIDER);
        headers.set("Idempotency-Key", key);
        return rest.postForEntity("/api/v1/rides", new HttpEntity<>(BODY, headers), String.class);
    }
}
