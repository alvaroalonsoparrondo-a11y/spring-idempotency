package io.github.idempotency.ride.booking;

import io.github.idempotency.ride.stripe.PaymentDeclinedException;
import io.github.idempotency.ride.stripe.PaymentFailedException;
import io.github.idempotency.ride.stripe.PaymentProviderException;
import io.github.idempotency.ride.stripe.StripeChargeClient;
import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestInfrastructure;
import io.github.idempotency.ride.web.ProblemTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a repeat of a booking gets after each kind of payment failure, through idempify and the database. Stripe is
 * replaced here only because stripe-mock answers every charge with success.
 */
@RequiresDocker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RecordedPaymentOutcomeIntegrationTest {

    private static final String RIDER = "rider@example.com";
    private static final String BODY = """
            {"origin": {"lat": 52.5200066, "lon": 13.404954}, "target": {"lat": 52.3675734, "lon": 4.9041389}}
            """;

    private final TestRestTemplate rest;
    private final JdbcClient jdbc;

    @MockitoBean
    private StripeChargeClient stripe;

    RecordedPaymentOutcomeIntegrationTest(TestRestTemplate rest, JdbcClient jdbc) {
        this.rest = rest;
        this.jdbc = jdbc;
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
    @DisplayName("IT post() when the card was declined should answer every repeat 402 without charging again")
    void post_whenCardWasDeclined_shouldAnswerEveryRepeat402WithoutChargingAgain() {
        // given
        UUID key = UUID.randomUUID();
        when(stripe.charge(anyString(), anyLong(), anyString(), anyString(), eq(key)))
                .thenThrow(new PaymentDeclinedException("The card was declined: insufficient_funds"));

        // when
        ResponseEntity<String> first = post(key);
        ResponseEntity<String> repeat = post(key);

        // then
        assertThat(first.getStatusCode()).as(first.getBody()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(repeat.getStatusCode()).as(repeat.getBody()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(repeat.getBody()).contains(ProblemTypes.PAYMENT_DECLINED.toString())
                .contains("The card was declined: insufficient_funds");
        verify(stripe, times(1)).charge(anyString(), anyLong(), anyString(), anyString(), eq(key));
    }

    @Test
    @DisplayName("IT post() when Stripe failed the charge for good should answer every repeat 502 without charging again")
    void post_whenStripeFailedChargeForGood_shouldAnswerEveryRepeat502WithoutChargingAgain() {
        // given
        UUID key = UUID.randomUUID();
        when(stripe.charge(anyString(), anyLong(), anyString(), anyString(), eq(key)))
                .thenThrow(new PaymentFailedException("Stripe failed the charge: InvalidRequestException"));

        // when
        ResponseEntity<String> first = post(key);
        ResponseEntity<String> repeat = post(key);

        // then
        assertThat(first.getStatusCode()).as(first.getBody()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(repeat.getStatusCode()).as(repeat.getBody()).isEqualTo(HttpStatus.BAD_GATEWAY);
        verify(stripe, times(1)).charge(anyString(), anyLong(), anyString(), anyString(), eq(key));
    }

    @Test
    @DisplayName("IT post() when Stripe was unavailable should let the repeat charge the same ride and answer 201")
    void post_whenStripeWasUnavailable_shouldLetRepeatChargeSameRideAndAnswerCreated() {
        // given
        UUID key = UUID.randomUUID();
        when(stripe.charge(anyString(), anyLong(), anyString(), anyString(), eq(key)))
                .thenThrow(new PaymentProviderException("Stripe did not complete the charge", new RuntimeException()))
                .thenReturn("ch_after_outage");

        // when
        ResponseEntity<String> first = post(key);
        ResponseEntity<String> repeat = post(key);

        // then
        assertThat(first.getStatusCode()).as(first.getBody()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(repeat.getStatusCode()).as(repeat.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeat.getBody()).contains("ch_after_outage");
        assertThat(jdbc.sql("SELECT count(*) FROM rides").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_records").query(Long.class).single()).isEqualTo(1L);
    }

    private ResponseEntity<String> post(UUID key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, RIDER);
        headers.set("Idempotency-Key", key.toString());
        return rest.postForEntity("/api/v1/rides", new HttpEntity<>(BODY, headers), String.class);
    }
}
