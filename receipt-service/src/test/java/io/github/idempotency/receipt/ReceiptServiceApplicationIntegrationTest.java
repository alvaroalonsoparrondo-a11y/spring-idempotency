package io.github.idempotency.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.receipt.sending.ReceiptSender;
import io.github.idempotency.receipt.support.ReceiptJobs;
import io.github.idempotency.receipt.support.RequiresDocker;
import io.github.idempotency.receipt.support.TestInfrastructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static io.github.idempotency.receipt.support.Waits.awaitAtMost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RequiresDocker
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ReceiptServiceApplicationIntegrationTest {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    // a spy, not a stub: it counts the receipts the real sender sends
    @MockitoSpyBean
    private ReceiptSender receiptSender;

    ReceiptServiceApplicationIntegrationTest(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry);
    }

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE inbox_events").update();
        clearInvocations(receiptSender);
    }

    @Test
    @DisplayName("IT listen() when a job is delivered twice under one event id should send one receipt")
    void listen_whenJobIsDeliveredTwiceUnderOneEventId_shouldSendOneReceipt() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(ReceiptJobs.job(7L));

        // the topic has one partition, so once the marker is handled both deliveries before it are too
        UUID marker = UUID.randomUUID();

        // when
        ReceiptJobs.publish(eventId, payload);
        ReceiptJobs.publish(eventId, payload);
        ReceiptJobs.publish(marker, objectMapper.writeValueAsString(ReceiptJobs.job(8L)));

        // then
        awaitAtMost().untilAsserted(() -> assertThat(
                jdbc.sql("SELECT id FROM inbox_events").query(UUID.class).list()
        ).containsExactlyInAnyOrder(eventId, marker));
        verify(receiptSender, times(1)).send(ReceiptJobs.job(7L));
        verify(receiptSender, times(2)).send(any());
    }
}
