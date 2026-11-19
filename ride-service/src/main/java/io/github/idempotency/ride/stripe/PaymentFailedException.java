package io.github.idempotency.ride.stripe;

/**
 * Stripe answered the charge with a failure a retry cannot cure: a request it rejects, a key or permission problem,
 * an error it records under the idempotency key and repeats to every retry. The answer is final for the request.
 * <p>
 * Kept on the idempotency record and rebuilt from its message on a repeat, hence the public constructor taking the
 * message alone.
 */
public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(String message) {
        super(message);
    }
}
