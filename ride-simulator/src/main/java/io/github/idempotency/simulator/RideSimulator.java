package io.github.idempotency.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Books one ride the way a mobile client on a bad network does. Every booking gets a new Idempotency-Key; a share
 * of them lose their first answer, and the client retries under the same key, backing off, until the answer is
 * definitive. However often it retries, ride-service books and charges each key once.
 */
@Component
public class RideSimulator {

    private static final Logger log = LoggerFactory.getLogger(RideSimulator.class);

    /**
     * The share of bookings whose first answer is lost.
     */
    static final double FAILURE_RATE = 0.3;

    static final Duration FIRST_BACKOFF = Duration.ofMillis(200);

    static final int MAX_ATTEMPTS = 8;

    private final RideClient client;
    private final SimulatorProperties properties;
    private final RandomGenerator random;
    private final Sleeper sleeper;

    @Autowired
    public RideSimulator(RideClient client, SimulatorProperties properties) {
        this(client, properties, RandomGenerator.getDefault(), Sleeper.THREAD);
    }

    RideSimulator(RideClient client, SimulatorProperties properties, RandomGenerator random, Sleeper sleeper) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper cannot be null");
    }

    /**
     * @return the definitive answer, or empty when none came within {@link #MAX_ATTEMPTS} attempts
     */
    public Optional<BookingAnswer> bookOnce() {
        String rider = properties.riders().get(random.nextInt(properties.riders().size()));
        UUID key = UUID.randomUUID();
        String body = randomRide();
        boolean loseFirstAnswer = random.nextDouble() < FAILURE_RATE;
        Duration backoff = FIRST_BACKOFF;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Optional<BookingAnswer> answer = Optional.empty();
            if (attempt == 1 && loseFirstAnswer) {
                client.bookAndHangUp(rider, key, body);
            } else {
                answer = Optional.of(client.book(rider, key, body));
            }
            if (answer.isPresent() && answer.get().isDefinitive()) {
                log.info("Booking {} for {} answered {} after {} attempt(s): {}",
                        key, rider, answer.get().status(), attempt, answer.get().body());
                return answer;
            }
            log.info("Booking {} for {} attempt {} got {}, retrying under the same key",
                    key, rider, attempt, answer.map(a -> "status " + a.status()).orElse("no answer"));
            sleeper.sleep(backoff);
            backoff = backoff.multipliedBy(2);
        }
        log.warn("Booking {} for {} got no definitive answer in {} attempts", key, rider, MAX_ATTEMPTS);
        return Optional.empty();
    }

    private String randomRide() {
        return "{\"origin\": %s, \"target\": %s}".formatted(randomPoint(), randomPoint());
    }

    private String randomPoint() {
        double lat = random.nextDouble(-60, 60);
        double lon = random.nextDouble(-180, 180);
        return String.format(Locale.ROOT, "{\"lat\": %.7f, \"lon\": %.7f}", lat, lon);
    }

    /**
     * Pauses between attempts; replaced in tests so that no test waits a real backoff.
     */
    interface Sleeper {

        Sleeper THREAD = duration -> {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted between attempts", e);
            }
        };

        void sleep(Duration duration);
    }
}
