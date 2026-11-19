package io.github.idempotency.ride.user;

/**
 * A rider, with the Stripe customer every charge of theirs goes to.
 */
public record User(long id, String email, String stripeCustomerId) {
}
