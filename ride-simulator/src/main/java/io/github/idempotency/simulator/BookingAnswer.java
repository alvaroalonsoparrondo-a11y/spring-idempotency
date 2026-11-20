package io.github.idempotency.simulator;

/**
 * What ride-service answered to one attempt of a booking.
 *
 * @param status the HTTP status
 * @param body   the body as it came
 */
record BookingAnswer(int status, String body) {

    /**
     * 409 means the first attempt under the key is still running; any other answer is the booking's outcome and is
     * what every later attempt under the key gets as well.
     */
    boolean isDefinitive() {
        return status != 409;
    }
}
