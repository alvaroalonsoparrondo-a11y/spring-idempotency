package io.github.idempotency.ride.stripe;

/**
 * Stripe could not be asked, did not answer, or asked to come back later: a network failure, a timeout, a rate
 * limit, the same key still being processed. Either the charge did not run or its outcome is unknown, so the request
 * is safe to retry under the same key - Stripe receives that key too and does not charge twice.
 */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
