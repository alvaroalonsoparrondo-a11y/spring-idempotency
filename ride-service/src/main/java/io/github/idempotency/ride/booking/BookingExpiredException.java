package io.github.idempotency.ride.booking;

/**
 * The ride was booked under this key but never charged, and too long ago for Stripe to still recognise the key: a
 * charge now could not be told apart from one Stripe may already have made, so none is made.
 */
public class BookingExpiredException extends RuntimeException {

    public BookingExpiredException(long rideId) {
        super("Ride %d was booked too long ago to be charged under the same Idempotency-Key".formatted(rideId));
    }
}
