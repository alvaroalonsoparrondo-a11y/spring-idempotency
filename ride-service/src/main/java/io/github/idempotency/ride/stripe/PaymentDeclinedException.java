package io.github.idempotency.ride.stripe;

/**
 * Stripe declined the card. The answer is final for the request: a retry under the same key gets the same refusal
 * instead of a second attempt to charge.
 * <p>
 * Kept on the idempotency record and rebuilt from its message on a repeat, hence the public constructor taking the
 * message alone.
 */
public class PaymentDeclinedException extends RuntimeException {

    public PaymentDeclinedException(String message) {
        super(message);
    }
}
