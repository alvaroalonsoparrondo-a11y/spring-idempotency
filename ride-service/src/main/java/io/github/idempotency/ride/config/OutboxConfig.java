package io.github.idempotency.ride.config;

import io.github.dmitriyiliyov.oncebox.starter.EnableOncebox;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Switches the oncebox outbox on in a configuration of its own rather than on the application class, so web slice
 * tests, which leave configurations out, do not start it.
 */
@Configuration(proxyBeanMethods = false)
@EnableOncebox
public class OutboxConfig {

    static final String RECEIPTS_TOPIC = "ride-receipts";

    /**
     * Created on startup when missing; an existing topic is left as it is.
     */
    @Bean
    public NewTopic rideReceiptsTopic(@Value("${rides.receipts.topic.partitions}") int partitions,
                                      @Value("${rides.receipts.topic.replicas}") short replicas) {
        return TopicBuilder.name(RECEIPTS_TOPIC).partitions(partitions).replicas(replicas).build();
    }
}
