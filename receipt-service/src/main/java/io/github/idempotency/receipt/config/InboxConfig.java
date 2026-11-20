package io.github.idempotency.receipt.config;

import io.github.dmitriyiliyov.oncebox.starter.EnableOncebox;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * The oncebox inbox and what happens to a receipt job that keeps failing.
 */
@Configuration(proxyBeanMethods = false)
@EnableOncebox
public class InboxConfig {

    /**
     * A job that still fails after its retries is published to {@code ride-receipts-dlt} and only then committed,
     * instead of the default of logging it and moving on, which would lose the receipt.
     */
    @Bean
    public CommonErrorHandler receiptJobErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), new FixedBackOff(1000L, 3L));
    }
}
