package io.github.idempotency.load;

import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.listFeeder;
import static io.gatling.javaapi.core.CoreDsl.percent;
import static io.gatling.javaapi.core.CoreDsl.randomSwitch;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Bookings at a constant arrival rate, a share of them sent a second time under the same Idempotency-Key, the way a
 * client retries after losing an answer. Every repeat has to come back with the first answer, byte for byte.
 * <p>
 * Tuned by system properties: {@code baseUrl}, {@code usersPerSecond}, {@code durationSeconds}.
 */
public class RideBookingSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final int USERS_PER_SECOND = Integer.getInteger("usersPerSecond", 10);
    private static final Duration DURATION = Duration.ofSeconds(Long.getLong("durationSeconds", 60));
    private static final double DUPLICATE_PERCENT = 20.0;

    private static final FeederBuilder<Object> RIDERS = listFeeder(List.<Map<String, Object>>of(
            Map.of("rider", "local.user@example.com"),
            Map.of("rider", "second.user@example.com"),
            Map.of("rider", "third.user@example.com")
    )).circular();

    private final HttpProtocolBuilder protocol = http.baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json");

    private final ScenarioBuilder booking = scenario("Book a ride")
            .feed(RIDERS)
            .exec(session -> session.set("key", UUID.randomUUID().toString()).set("body", randomRide()))
            .exec(book("book").check(status().is(201), bodyString().saveAs("firstAnswer")))
            .exec(randomSwitch().on(percent(DUPLICATE_PERCENT).then(exec(book("repeat under the same key")
                    .check(status().is(201), bodyString().isEL("#{firstAnswer}"))))));

    {
        setUp(booking.injectOpen(constantUsersPerSec(USERS_PER_SECOND).during(DURATION)))
                .protocols(protocol)
                .assertions(
                        global().successfulRequests().percent().gt(99.0),
                        global().responseTime().percentile(95.0).lt(1000)
                );
    }

    private static HttpRequestActionBuilder book(String name) {
        return http(name).post("/api/v1/rides")
                .header("Authorization", "#{rider}")
                .header("Idempotency-Key", "#{key}")
                .body(StringBody("#{body}"));
    }

    private static String randomRide() {
        return "{\"origin\": %s, \"target\": %s}".formatted(randomPoint(), randomPoint());
    }

    private static String randomPoint() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return String.format(Locale.ROOT, "{\"lat\": %.7f, \"lon\": %.7f}",
                random.nextDouble(-60, 60), random.nextDouble(-180, 180));
    }
}
