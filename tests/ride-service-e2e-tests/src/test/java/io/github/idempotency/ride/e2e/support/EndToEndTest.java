package io.github.idempotency.ride.e2e.support;

import io.github.idempotency.ride.RideServiceApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * The service as it runs in production - its own application class and configuration on a real port - over
 * PostgreSQL and stripe-mock, shared by every end-to-end class. The riders come from the service's seed migration,
 * since the API has no way to register one.
 */
@RequiresDocker
@SpringBootTest(classes = RideServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class EndToEndTest {

    protected static final String RIDER = "local.user@example.com";
    protected static final String SECOND_RIDER = "second.user@example.com";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17.12-alpine")
    ).withDatabaseName("rides");

    private static final GenericContainer<?> STRIPE_MOCK = new GenericContainer<>(
            DockerImageName.parse("stripe/stripe-mock:v0.205.0")
    ).withExposedPorts(12111).waitingFor(Wait.forListeningPort());

    static {
        POSTGRES.start();
        STRIPE_MOCK.start();
    }

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/seed");
        registry.add("stripe.api-key", () -> "sk_test_123");
        registry.add("stripe.api-base", () -> "http://%s:%d".formatted(
                STRIPE_MOCK.getHost(),
                STRIPE_MOCK.getMappedPort(12111)
        ));
    }

    protected RidesClient client() {
        return new RidesClient(port);
    }
}
