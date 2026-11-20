package io.github.idempotency.ride.support;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

import java.time.Duration;

public final class Waits {

    private static final Duration AT_MOST = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private Waits() {}

    public static ConditionFactory awaitAtMost() {
        return Awaitility.await().atMost(AT_MOST).pollInterval(POLL_INTERVAL);
    }
}
