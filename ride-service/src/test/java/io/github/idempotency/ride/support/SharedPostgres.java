package io.github.idempotency.ride.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL per test JVM for every test that starts a Spring context: a cached context outlives the class
 * that created it, so its container must too.
 */
public final class SharedPostgres {

    private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>(TestImages.POSTGRES)
            .withDatabaseName("rides");

    private SharedPostgres() {}

    public static void register(DynamicPropertyRegistry registry) {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
    }
}
