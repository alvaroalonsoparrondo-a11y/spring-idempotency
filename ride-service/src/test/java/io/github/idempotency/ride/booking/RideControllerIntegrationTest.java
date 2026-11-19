package io.github.idempotency.ride.booking;

import io.github.dmitriyiliyov.idempify.core.IdempotencyKeyException;
import io.github.idempotency.ride.stripe.PaymentDeclinedException;
import io.github.idempotency.ride.stripe.PaymentFailedException;
import io.github.idempotency.ride.stripe.PaymentProviderException;
import io.github.idempotency.ride.web.Caller;
import io.github.idempotency.ride.web.IdempifyAdviceConfig;
import io.github.idempotency.ride.web.ProblemTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static io.github.idempotency.ride.booking.TestRides.KEY;
import static io.github.idempotency.ride.booking.TestRides.USER;
import static io.github.idempotency.ride.booking.TestRides.charged;
import static io.github.idempotency.ride.booking.TestRides.request;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RideController.class)
@Import(IdempifyAdviceConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RideControllerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:15:30Z");
    private static final Caller CALLER = new Caller(USER, "203.0.113.7");
    private static final String BODY = """
            {"origin": {"lat": 52.5200066, "lon": 13.4049540},
             "target": {"lat": 52.3675734, "lon": 4.9041389},
             "client": {"appVersion": "4.2.0", "platform": "ios"}}
            """;

    private final MockMvc mvc;

    @MockitoBean
    private RideService rideService;

    RideControllerIntegrationTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("IT create() when the ride is booked should answer 201 with the charged ride")
    void create_whenRideIsBooked_shouldAnswerCreatedWithChargedRide() throws Exception {
        // given
        when(rideService.create(USER, "203.0.113.7", KEY, request()))
                .thenReturn(RideResponse.from(charged(7L, "ch_123")));

        // when
        ResultActions result = post(BODY);

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.origin.lat").value(52.5200066))
                .andExpect(jsonPath("$.target.lon").value(4.9041389))
                .andExpect(jsonPath("$.amount").value(2000))
                .andExpect(jsonPath("$.currency").value("usd"))
                .andExpect(jsonPath("$.stripeChargeId").value("ch_123"))
                .andExpect(jsonPath("$.chargedAt").value("2026-11-18T10:15:30Z"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-18T10:15:00Z"));
    }

    @Test
    @DisplayName("IT create() when coordinates are missing or out of range should answer 400 naming every field")
    void create_whenCoordinatesAreMissingOrOutOfRange_shouldAnswerBadRequestNamingEveryField() throws Exception {
        // when
        ResultActions result = post("""
                {"origin": {"lat": 91, "lon": -181}, "client": {"appVersion": "%s"}}
                """.formatted("9".repeat(33)));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ProblemTypes.VALIDATION_FAILED.toString()))
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/v1/rides"))
                .andExpect(jsonPath("$.path").value("/api/v1/rides"))
                .andExpect(jsonPath("$.timestamp").value(NOW.toString()))
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder(
                        "origin.lat",
                        "origin.lon",
                        "target",
                        "client.appVersion"
                )))
                .andExpect(jsonPath("$.errors[*].message", containsInAnyOrder(
                        "lat must be at most 90",
                        "lon must be at least -180",
                        "target must not be null",
                        "appVersion must be at most 32 characters"
                )));
        verifyNoInteractions(rideService);
    }

    @Test
    @DisplayName("IT create() when the body is not JSON should answer 400 with Spring's problem stamped")
    void create_whenBodyIsNotJson_shouldAnswerBadRequestWithSpringProblemStamped() throws Exception {
        // when
        ResultActions result = post("{not json");

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.path").value("/api/v1/rides"))
                .andExpect(jsonPath("$.timestamp").value(NOW.toString()));
    }

    @Test
    @DisplayName("IT create() when the card is declined should answer 402 with the reason")
    void create_whenCardIsDeclined_shouldAnswerPaymentRequiredWithReason() throws Exception {
        // given
        when(rideService.create(eq(USER), eq("203.0.113.7"), eq(KEY), any()))
                .thenThrow(new PaymentDeclinedException("The card was declined: insufficient_funds"));

        // when / then
        post(BODY).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.type").value(ProblemTypes.PAYMENT_DECLINED.toString()))
                .andExpect(jsonPath("$.title").value("Payment declined"))
                .andExpect(jsonPath("$.detail").value("The card was declined: insufficient_funds"));
    }

    @Test
    @DisplayName("IT create() when Stripe fails should answer 503 asking to retry without the cause")
    void create_whenStripeFails_shouldAnswerServiceUnavailableAskingToRetryWithoutCause() throws Exception {
        // given
        when(rideService.create(eq(USER), eq("203.0.113.7"), eq(KEY), any()))
                .thenThrow(new PaymentProviderException("Stripe did not complete the charge", new RuntimeException("io")));

        // when / then
        post(BODY).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value(ProblemTypes.PAYMENT_PROVIDER_UNAVAILABLE.toString()))
                .andExpect(jsonPath("$.detail")
                        .value("The payment could not be completed. Retry the request with the same Idempotency-Key."));
    }

    @Test
    @DisplayName("IT create() when Stripe fails the charge for good should answer 502 saying a retry repeats it")
    void create_whenStripeFailsChargeForGood_shouldAnswerBadGatewaySayingRetryRepeatsIt() throws Exception {
        // given
        when(rideService.create(eq(USER), eq("203.0.113.7"), eq(KEY), any()))
                .thenThrow(new PaymentFailedException("Stripe failed the charge: InvalidRequestException"));

        // when / then
        post(BODY).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value(ProblemTypes.PAYMENT_FAILED.toString()))
                .andExpect(jsonPath("$.detail").value(
                        "The payment provider failed the charge. A retry under the same Idempotency-Key gets the same answer."
                ));
    }

    @Test
    @DisplayName("IT create() when the key booked a different ride should answer 422 with idempify's reuse type")
    void create_whenKeyBookedDifferentRide_shouldAnswerUnprocessableWithIdempifyReuseType() throws Exception {
        // given
        when(rideService.create(eq(USER), eq("203.0.113.7"), eq(KEY), any()))
                .thenThrow(new IdempotencyKeyReusedException(KEY));

        // when / then
        post(BODY).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(
                        io.github.dmitriyiliyov.idempify.http.ProblemTypes.IDEMPOTENCY_KEY_REUSE.toString()
                ))
                .andExpect(jsonPath("$.title").value("Idempotency-Key reuse"));
    }

    @Test
    @DisplayName("IT create() when idempify refuses the call should keep idempify's answer, not the catch-all")
    void create_whenIdempifyRefusesCall_shouldKeepIdempifyAnswerNotCatchAll() throws Exception {
        // given
        when(rideService.create(eq(USER), eq("203.0.113.7"), eq(KEY), any()))
                .thenThrow(new IdempotencyKeyException("HTTP header Idempotency-Key is null or empty"));

        // when / then
        post(BODY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        io.github.dmitriyiliyov.idempify.http.ProblemTypes.INVALID_IDEMPOTENCY_KEY.toString()
                ));
    }

    @Test
    @DisplayName("IT create() when something unexpected fails should answer 500 with a fixed detail")
    void create_whenSomethingUnexpectedFails_shouldAnswerInternalErrorWithFixedDetail() throws Exception {
        // given
        when(rideService.create(eq(USER), eq("203.0.113.7"), eq(KEY), any()))
                .thenThrow(new IllegalStateException("relation \"rides\" does not exist"));

        // when / then
        post(BODY).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value(ProblemTypes.INTERNAL_ERROR.toString()))
                .andExpect(jsonPath("$.detail").value("The operation could not be completed."));
    }

    private ResultActions post(String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/rides")
                .requestAttr(Caller.ATTRIBUTE, CALLER)
                .header("Idempotency-Key", KEY.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
