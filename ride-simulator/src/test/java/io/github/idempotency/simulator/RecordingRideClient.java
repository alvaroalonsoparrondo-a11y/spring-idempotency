package io.github.idempotency.simulator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Answers bookings from a script, in order, and records every attempt it was asked to make.
 */
class RecordingRideClient extends RideClient {

    private final Deque<BookingAnswer> script = new ArrayDeque<>();
    private final List<Attempt> attempts = new ArrayList<>();

    RecordingRideClient() {
        super(new SimulatorProperties("http://localhost:8080", List.of("rider@example.com")));
    }

    RecordingRideClient answering(BookingAnswer answer) {
        script.add(answer);
        return this;
    }

    List<Attempt> attempts() {
        return attempts;
    }

    @Override
    public BookingAnswer book(String rider, UUID idempotencyKey, String body) {
        attempts.add(new Attempt(rider, idempotencyKey, body, false));
        return script.isEmpty() ? new BookingAnswer(409, "{}") : script.poll();
    }

    @Override
    public void bookAndHangUp(String rider, UUID idempotencyKey, String body) {
        attempts.add(new Attempt(rider, idempotencyKey, body, true));
    }

    record Attempt(String rider, UUID idempotencyKey, String body, boolean hungUp) {
    }
}
