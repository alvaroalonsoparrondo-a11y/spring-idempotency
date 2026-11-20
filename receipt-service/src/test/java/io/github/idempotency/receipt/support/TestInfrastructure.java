package io.github.idempotency.receipt.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL and one Kafka per test JVM for every test that starts a Spring context: a cached context outlives
 * the class that created it, so its containers must too.
 */
public final class TestInfrastructure {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17.12-alpine")
    ).withDatabaseName("receipts");

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.4.0"));

    private TestInfrastructure() {}

    public static void register(DynamicPropertyRegistry registry) {
        start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    public static String kafkaBootstrapServers() {
        start();
        return KAFKA.getBootstrapServers();
    }

    private static synchronized void start() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        if (!KAFKA.isRunning()) {
            KAFKA.start();
        }
    }
}
