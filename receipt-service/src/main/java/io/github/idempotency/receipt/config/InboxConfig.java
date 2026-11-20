package io.github.idempotency.receipt.config;

import io.github.dmitriyiliyov.oncebox.starter.EnableOncebox;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * The oncebox inbox, the topics it reads and writes, and what happens to a receipt job that keeps failing.
 */
@Configuration(proxyBeanMethods = false)
@EnableOncebox
public class InboxConfig {

    static final String RECEIPTS_TOPIC = "ride-receipts";

    static final String DEAD_LETTER_TOPIC = RECEIPTS_TOPIC + "-dlt";

    /**
     * Declared here as well as in ride-service: whichever service starts first creates it with the agreed
     * partitions. Left to the broker, a subscription made before ride-service starts would create a topic of one
     * partition, and the partitions added later stay unread until the consumer refreshes its metadata.
     */
    @Bean
    public NewTopic rideReceiptsTopic(@Value("${receipts.topic.partitions}") int partitions,
                                      @Value("${receipts.topic.replicas}") short replicas) {
        return TopicBuilder.name(RECEIPTS_TOPIC).partitions(partitions).replicas(replicas).build();
    }

    /**
     * As many partitions as the topic it backs: a dead letter keeps the partition its job came from.
     */
    @Bean
    public NewTopic rideReceiptsDeadLetterTopic(@Value("${receipts.topic.partitions}") int partitions,
                                                @Value("${receipts.topic.replicas}") short replicas) {
        return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(partitions).replicas(replicas).build();
    }

    /**
     * A job that still fails after its retries is published to {@code ride-receipts-dlt} and only then committed,
     * instead of the default of logging it and moving on, which would lose the receipt.
     */
    @Bean
    public CommonErrorHandler receiptJobErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), new FixedBackOff(1000L, 3L));
    }
}
