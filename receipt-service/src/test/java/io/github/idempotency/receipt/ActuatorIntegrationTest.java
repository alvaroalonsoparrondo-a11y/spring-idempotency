package io.github.idempotency.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.receipt.support.ReceiptJobs;
import io.github.idempotency.receipt.support.ReceiptServiceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static io.github.idempotency.receipt.support.Waits.awaitAtMost;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Health and metrics, as Docker, Kubernetes and Prometheus read them.
 */
class ActuatorIntegrationTest extends ReceiptServiceIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("IT health when the database is up should answer UP on every probe")
    void health_whenDatabaseIsUp_shouldAnswerUpOnEveryProbe() {
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
    @DisplayName("IT prometheus when a job was consumed should count it among the inbox events")
    void prometheus_whenJobWasConsumed_shouldCountItAmongInboxEvents() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        ReceiptJobs.publish(eventId, objectMapper.writeValueAsString(ReceiptJobs.job(12L)));
        awaitAtMost().until(() -> jdbc.sql("SELECT count(*) FROM inbox_events WHERE id = ?")
                .param(eventId)
                .query(Long.class)
                .single() == 1L);

        // when
        ResponseEntity<String> scrape = rest.getForEntity("/actuator/prometheus", String.class);

        // then
        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody())
                .containsPattern("inbox_events_total\\{[^}]*type=\"consumed\"[^}]*} [1-9]")
                .contains("application=\"receipt-service\"");
    }
}
