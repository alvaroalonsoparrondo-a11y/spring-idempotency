package io.github.idempotency.ride.booking;

/**
 * The outcome of recording a charge on a ride.
 *
 * @param ride     the ride as it is after the call
 * @param recorded whether this call recorded the charge; {@code false} when the same charge was already there
 */
record ChargeRecord(Ride ride, boolean recorded) {
}
