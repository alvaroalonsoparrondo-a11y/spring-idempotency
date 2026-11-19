package io.github.idempotency.ride.stripe;

import com.stripe.StripeClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfig {

    @Bean
    public StripeClient stripeClient(StripeProperties properties) {
        return StripeClient.builder()
                .setApiKey(properties.apiKey())
                .setApiBase(properties.apiBase())
                .setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
                .setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()))
                .setMaxNetworkRetries(properties.maxNetworkRetries())
                .build();
    }
}
