package io.github.idempotency.ride.booking;

import java.util.UUID;

/**
 * The key already booked a ride that differs from this request: another route, or another user. Reached only once
 * the key's idempotency record has expired; until then the same refusal comes from the fingerprint check.
 */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(UUID idempotencyKey) {
        super("Idempotency-Key %s already booked a different ride".formatted(idempotencyKey));
    }
}
