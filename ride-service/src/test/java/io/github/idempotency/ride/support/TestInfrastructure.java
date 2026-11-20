package io.github.idempotency.ride.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * One PostgreSQL, one Redis and one stripe-mock per test JVM for every test that starts a Spring context: a cached context
 * outlives the class that created it, so its containers must too.
 */
public final class TestInfrastructure {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TestImages.POSTGRES)
            .withDatabaseName("rides");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(TestImages.REDIS)
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    private static final GenericContainer<?> STRIPE_MOCK = new GenericContainer<>(TestImages.STRIPE_MOCK)
            .withExposedPorts(TestImages.STRIPE_MOCK_PORT)
            .waitingFor(Wait.forListeningPort());

    private TestInfrastructure() {}

    public static void register(DynamicPropertyRegistry registry) {
        start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("stripe.api-key", () -> "sk_test_123");
        registry.add("stripe.api-base", TestInfrastructure::stripeMockUrl);
    }

    public static String stripeMockUrl() {
        start();
        return "http://%s:%d".formatted(STRIPE_MOCK.getHost(), STRIPE_MOCK.getMappedPort(TestImages.STRIPE_MOCK_PORT));
    }

    private static synchronized void start() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        if (!REDIS.isRunning()) {
            REDIS.start();
        }
        if (!STRIPE_MOCK.isRunning()) {
            STRIPE_MOCK.start();
        }
    }
}
