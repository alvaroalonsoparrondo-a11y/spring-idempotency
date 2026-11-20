package io.github.idempotency.receipt.support;

import io.github.idempotency.receipt.sending.ReceiptRepository;
import io.github.idempotency.receipt.sending.ReceiptSender;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.reset;

/**
 * The one context every receipt-service test that starts the application shares. One context means one consumer in
 * the service's group: a second cached context would join the group and take part of the jobs away.
 * <p>
 * The sender and the repository are spies - the real beans, counted, and made to fail where a case needs it.
 */
@RequiresDocker
// tests leave metrics export off unless asked; the Prometheus endpoint is part of what is tested
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ReceiptServiceIntegrationTest {

    @MockitoSpyBean
    protected ReceiptSender receiptSender;

    @MockitoSpyBean
    protected ReceiptRepository receiptRepository;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected TestRestTemplate rest;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfrastructure.register(registry);
    }

    @BeforeEach
    void resetState() {
        jdbc.sql("TRUNCATE inbox_events, receipts RESTART IDENTITY").update();
        reset(receiptRepository);
        clearInvocations(receiptSender);
    }
}
