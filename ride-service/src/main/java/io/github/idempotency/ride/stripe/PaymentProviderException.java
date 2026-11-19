package io.github.idempotency.ride.stripe;

/**
 * Stripe could not be asked or did not answer the charge: a network failure, a timeout, an error on its side. The
 * outcome of the charge is unknown, so the request is safe to retry under the same key - Stripe receives that key
 * too and does not charge twice.
 */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
