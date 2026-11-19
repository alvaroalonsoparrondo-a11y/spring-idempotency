package io.github.idempotency.ride.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.CardException;
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
     * @throws PaymentProviderException  when Stripe could not be reached or failed to answer
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
            throw new PaymentProviderException("Stripe did not complete the charge", e);
        }
    }

    private static String declinedMessage(CardException e) {
        String reason = e.getDeclineCode() != null ? e.getDeclineCode() : e.getCode();
        return reason != null ? "The card was declined: %s".formatted(reason) : "The card was declined";
    }
}
