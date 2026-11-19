package io.github.idempotency.ride.booking;

import java.util.UUID;

/**
 * A ride about to be stored, before the database has given it an id.
 */
record NewRide(long userId,
               UUID idempotencyKey,
               Coordinates origin,
               Coordinates target,
               long amount,
               String currency) {
}
