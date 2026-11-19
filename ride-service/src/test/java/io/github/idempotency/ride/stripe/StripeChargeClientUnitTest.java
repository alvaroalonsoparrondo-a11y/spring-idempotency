package io.github.idempotency.ride.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.net.RequestOptions;
import com.stripe.param.ChargeCreateParams;
import com.stripe.service.ChargeService;
import com.stripe.service.V1Services;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeChargeClientUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    @Mock
    private StripeClient stripe;

    @Mock
    private V1Services v1;

    @Mock
    private ChargeService charges;

    private StripeChargeClient tested;

    @BeforeEach
    void setUp() {
        tested = new StripeChargeClient(stripe);
    }

    @Test
    @DisplayName("UT charge() when Stripe accepts the charge should return its id")
    void charge_whenStripeAcceptsCharge_shouldReturnItsId() throws Exception {
        // given
        givenChargeService();
        Charge charge = new Charge();
        charge.setId("ch_123");
        when(charges.create(any(ChargeCreateParams.class), any(RequestOptions.class))).thenReturn(charge);

        // when
        String chargeId = tested.charge("cus_rider", 2000L, "usd", "Ride 7", KEY);

        // then
        assertThat(chargeId).isEqualTo("ch_123");
    }

    @Test
    @DisplayName("UT charge() should send the customer, amount and currency under the request's idempotency key")
    void charge_whenCalled_shouldSendChargeUnderRequestIdempotencyKey() throws Exception {
        // given
        givenChargeService();
        Charge charge = new Charge();
        charge.setId("ch_123");
        ArgumentCaptor<ChargeCreateParams> params = ArgumentCaptor.forClass(ChargeCreateParams.class);
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        when(charges.create(params.capture(), options.capture())).thenReturn(charge);

        // when
        tested.charge("cus_rider", 2000L, "usd", "Ride 7", KEY);

        // then
        assertThat(params.getValue().getCustomer()).isEqualTo("cus_rider");
        assertThat(params.getValue().getAmount()).isEqualTo(2000L);
        assertThat(params.getValue().getCurrency()).isEqualTo("usd");
        assertThat(params.getValue().getDescription()).isEqualTo("Ride 7");
        assertThat(options.getValue().getIdempotencyKey()).isEqualTo(KEY.toString());
    }

    @Test
    @DisplayName("UT charge() when the card is declined with a decline code should throw PaymentDeclinedException")
    void charge_whenCardIsDeclinedWithDeclineCode_shouldThrowPaymentDeclinedException() throws Exception {
        // given
        givenChargeService();
        when(charges.create(any(ChargeCreateParams.class), any(RequestOptions.class)))
                .thenThrow(cardException("card_declined", "insufficient_funds"));

        // when / then
        assertThatThrownBy(() -> tested.charge("cus_rider", 2000L, "usd", "Ride 7", KEY))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessage("The card was declined: insufficient_funds");
    }

    @Test
    @DisplayName("UT charge() when the card is declined without a decline code should name the error code")
    void charge_whenCardIsDeclinedWithoutDeclineCode_shouldNameErrorCode() throws Exception {
        // given
        givenChargeService();
        when(charges.create(any(ChargeCreateParams.class), any(RequestOptions.class)))
                .thenThrow(cardException("expired_card", null));

        // when / then
        assertThatThrownBy(() -> tested.charge("cus_rider", 2000L, "usd", "Ride 7", KEY))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessage("The card was declined: expired_card");
    }

    @Test
    @DisplayName("UT charge() when the card is declined with no code at all should say only that it was declined")
    void charge_whenCardIsDeclinedWithNoCode_shouldSayOnlyThatItWasDeclined() throws Exception {
        // given
        givenChargeService();
        when(charges.create(any(ChargeCreateParams.class), any(RequestOptions.class)))
                .thenThrow(cardException(null, null));

        // when / then
        assertThatThrownBy(() -> tested.charge("cus_rider", 2000L, "usd", "Ride 7", KEY))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessage("The card was declined");
    }

    @Test
    @DisplayName("UT charge() when Stripe cannot be reached should throw PaymentProviderException with the cause")
    void charge_whenStripeCannotBeReached_shouldThrowPaymentProviderExceptionWithCause() throws Exception {
        // given
        givenChargeService();
        ApiConnectionException cause = new ApiConnectionException("connection refused");
        when(charges.create(any(ChargeCreateParams.class), any(RequestOptions.class))).thenThrow(cause);

        // when / then
        assertThatThrownBy(() -> tested.charge("cus_rider", 2000L, "usd", "Ride 7", KEY))
                .isInstanceOf(PaymentProviderException.class)
                .hasCause(cause);
        verify(charges).create(any(ChargeCreateParams.class), any(RequestOptions.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("retryableFailures")
    @DisplayName("UT charge() when Stripe asks to come back later should throw PaymentProviderException")
    void charge_whenStripeAsksToComeBackLater_shouldThrowPaymentProviderException(String name, StripeException cause)
            throws Exception {
        // given
        givenChargeService();
        when(charges.create(any(ChargeCreateParams.class), any(RequestOptions.class))).thenThrow(cause);

        // when / then
        assertThatThrownBy(() -> tested.charge("cus_rider", 2000L, "usd", "Ride 7", KEY))
                .isInstanceOf(PaymentProviderException.class)
                .hasCause(cause);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("finalFailures")
    @DisplayName("UT charge() when Stripe fails the charge in a way a retry would repeat should throw PaymentFailedException")
    void charge_whenStripeFailsChargeInWayRetryWouldRepeat_shouldThrowPaymentFailedException(String name,
                                                                                             StripeException cause)
            throws Exception {
        // given
        givenChargeService();
        when(charges.create(any(ChargeCreateParams.class), any(RequestOptions.class))).thenThrow(cause);

        // when / then
        assertThatThrownBy(() -> tested.charge("cus_rider", 2000L, "usd", "Ride 7", KEY))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessage("Stripe failed the charge: %s (status %s, request req_1)".formatted(
                        cause.getClass().getSimpleName(),
                        cause.getStatusCode()
                ));
    }

    @Test
    @DisplayName("UT constructor when stripe is null should throw NullPointerException")
    void constructor_whenStripeIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new StripeChargeClient(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stripe cannot be null");
    }

    private void givenChargeService() {
        when(stripe.v1()).thenReturn(v1);
        when(v1.charges()).thenReturn(charges);
    }

    static Stream<Arguments> retryableFailures() {
        return Stream.of(
                Arguments.of("rate limited", new RateLimitException("slow down", null, "req_1", "rate_limit", 429, null)),
                Arguments.of("key in flight", new IdempotencyException("in use", "req_1", "idempotency_key_in_use", 409))
        );
    }

    static Stream<Arguments> finalFailures() {
        return Stream.of(
                Arguments.of("no such customer",
                        new InvalidRequestException("No such customer", "customer", "req_1", "resource_missing", 400, null)),
                Arguments.of("key reused with other parameters",
                        new IdempotencyException("params differ", "req_1", "idempotency_error", 400)),
                Arguments.of("invalid api key", new AuthenticationException("bad key", "req_1", null, 401)),
                Arguments.of("error on Stripe's side", new ApiException("oops", "req_1", null, 500, null))
        );
    }

    private static CardException cardException(String code, String declineCode) {
        return new CardException("declined", "req_1", code, "card", declineCode, null, 402, null);
    }
}
