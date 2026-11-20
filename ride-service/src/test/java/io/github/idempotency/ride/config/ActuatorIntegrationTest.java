package io.github.idempotency.ride.config;

import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestInfrastructure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Health and metrics, as Docker, Kubernetes and Prometheus read them.
 */
@RequiresDocker
// tests leave metrics export off unless asked; Prometheus is what is under test here
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ActuatorIntegrationTest {

    private final TestRestTemplate rest;
    private final JdbcClient jdbc;

    ActuatorIntegrationTest(TestRestTemplate rest, JdbcClient jdbc) {
        this.rest = rest;
        this.jdbc = jdbc;
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry);
    }

    @Test
    @DisplayName("IT health when the database and Redis are up should answer UP without the Authorization header")
    void health_whenDatabaseAndRedisAreUp_shouldAnswerUpWithoutAuthorizationHeader() {
        // when
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        ResponseEntity<String> liveness = rest.getForEntity("/actuator/health/liveness", String.class);
        ResponseEntity<String> readiness = rest.getForEntity("/actuator/health/readiness", String.class);

        // then
        assertThat(health.getStatusCode()).as(health.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("IT prometheus when a ride was booked should count it among the idempotent operations")
    void prometheus_whenRideWasBooked_shouldCountItAmongIdempotentOperations() {
        // given
        jdbc.sql("INSERT INTO users (email, stripe_customer_id) VALUES ('metrics@example.com', 'cus_metrics') "
                + "ON CONFLICT DO NOTHING").update();
        book();

        // when
        ResponseEntity<String> scrape = rest.getForEntity("/actuator/prometheus", String.class);

        // then
        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody())
                .containsPattern("idempify_operations_total\\{[^}]*outcome=\"success\"[^}]*} [1-9]")
                .contains("idempify_cache_gets_total")
                .contains("oncebox_task_processing_duration")
                .contains("application=\"ride-service\"");
    }

    private void book() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "metrics@example.com");
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        String body = "{\"origin\": {\"lat\": 52.52, \"lon\": 13.405}, \"target\": {\"lat\": 52.37, \"lon\": 4.9}}";
        ResponseEntity<String> booked = rest.postForEntity(
                "/api/v1/rides",
                new HttpEntity<>(body, headers),
                String.class
        );
        assertThat(booked.getStatusCode()).as(booked.getBody()).isEqualTo(HttpStatus.CREATED);
    }
}
