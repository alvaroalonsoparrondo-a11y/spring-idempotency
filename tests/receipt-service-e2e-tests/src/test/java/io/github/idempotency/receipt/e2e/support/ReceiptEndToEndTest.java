package io.github.idempotency.receipt.e2e.support;

import io.github.idempotency.ride.RideServiceApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Both services as they run in production: ride-service from its application class on a real port, receipt-service
 * from its executable jar in a process of its own, with a database each, Redis, Kafka and stripe-mock between them.
 * The riders come from ride-service's seed migration; the receipts are read from receipt-service's database, where
 * they land.
 */
@RequiresDocker
@SpringBootTest(classes = RideServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ReceiptEndToEndTest {

    protected static final String RIDER = "local.user@example.com";

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17.12-alpine");

    private static final PostgreSQLContainer<?> RIDES_DB = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("rides");

    private static final PostgreSQLContainer<?> RECEIPTS_DB = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("receipts");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.8.4-alpine")
    ).withExposedPorts(6379).waitingFor(Wait.forListeningPort());

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.4.0"));

    private static final GenericContainer<?> STRIPE_MOCK = new GenericContainer<>(
            DockerImageName.parse("stripe/stripe-mock:v0.205.0")
    ).withExposedPorts(12111).waitingFor(Wait.forListeningPort());

    static {
        RIDES_DB.start();
        RECEIPTS_DB.start();
        REDIS.start();
        KAFKA.start();
        STRIPE_MOCK.start();
        ReceiptServiceProcess.start(Map.of(
                "RECEIPT_DB_URL", RECEIPTS_DB.getJdbcUrl(),
                "RECEIPT_DB_USERNAME", RECEIPTS_DB.getUsername(),
                "RECEIPT_DB_PASSWORD", RECEIPTS_DB.getPassword(),
                "RECEIPT_KAFKA_BOOTSTRAP_SERVERS", KAFKA.getBootstrapServers()
        ));
    }

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", RIDES_DB::getJdbcUrl);
        registry.add("spring.datasource.username", RIDES_DB::getUsername);
        registry.add("spring.datasource.password", RIDES_DB::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/seed");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("stripe.api-key", () -> "sk_test_123");
        registry.add("stripe.api-base", () -> "http://%s:%d".formatted(
                STRIPE_MOCK.getHost(),
                STRIPE_MOCK.getMappedPort(12111)
        ));
    }

    protected String baseUrl() {
        return "http://localhost:%d".formatted(port);
    }

    protected static JdbcClient receipts() {
        return JdbcClient.create(new DriverManagerDataSource(
                RECEIPTS_DB.getJdbcUrl(),
                RECEIPTS_DB.getUsername(),
                RECEIPTS_DB.getPassword()
        ));
    }
}
