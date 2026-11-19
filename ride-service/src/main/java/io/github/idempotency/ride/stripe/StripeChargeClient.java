package io.github.idempotency.ride.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.CardException;
import com.stripe.exception.IdempotencyException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.net.RequestOptions;
import com.stripe.param.ChargeCreateParams;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Charges a customer through Stripe, passing the request's idempotency key on to Stripe as its own: whichever
 * retry reaches Stripe, the customer is charged once per key.
 */
@Component
public class StripeChargeClient {

    private final StripeClient stripe;

    public StripeChargeClient(StripeClient stripe) {
        this.stripe = Objects.requireNonNull(stripe, "stripe cannot be null");
    }

    /**
     * @param customerId     the Stripe customer to charge
     * @param amount         the amount in the smallest currency unit
     * @param currency       the lowercase ISO 4217 code
     * @param description    what the charge is for, shown on the customer's statement
     * @param idempotencyKey the key of the request the charge is made for
     * @return the id of the Stripe charge
     * @throws PaymentDeclinedException  when Stripe declined the card
     * @throws PaymentFailedException    when Stripe failed the charge in a way a retry would only repeat
     * @throws PaymentProviderException  when Stripe could not be reached or asked to retry later
     */
    public String charge(String customerId, long amount, String currency, String description, UUID idempotencyKey) {
        ChargeCreateParams params = ChargeCreateParams.builder()
                .setCustomer(customerId)
                .setAmount(amount)
                .setCurrency(currency)
                .setDescription(description)
                .build();
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey.toString())
                .build();
        try {
            Charge charge = stripe.v1().charges().create(params, options);
            return charge.getId();
        } catch (CardException e) {
            throw new PaymentDeclinedException(declinedMessage(e));
        } catch (StripeException e) {
            if (isRetryable(e)) {
                throw new PaymentProviderException("Stripe did not complete the charge", e);
            }
            throw new PaymentFailedException(failedMessage(e));
        }
    }

    /**
     * Only a failure that leaves nothing recorded under the key is worth a retry: the request never got an answer,
     * was rate limited, or met the same key still in flight. Stripe records any other answer under the key and
     * repeats it to every retry for as long as it keeps the key.
     */
    private static boolean isRetryable(StripeException e) {
        if (e instanceof ApiConnectionException || e instanceof RateLimitException) {
            return true;
        }
        return e instanceof IdempotencyException && Integer.valueOf(409).equals(e.getStatusCode());
    }

    private static String failedMessage(StripeException e) {
        return "Stripe failed the charge: %s (status %s, request %s)".formatted(
                e.getClass().getSimpleName(),
                e.getStatusCode(),
                e.getRequestId()
        );
    }

    private static String declinedMessage(CardException e) {
        String reason = e.getDeclineCode() != null ? e.getDeclineCode() : e.getCode();
        return reason != null ? "The card was declined: %s".formatted(reason) : "The card was declined";
    }
}
