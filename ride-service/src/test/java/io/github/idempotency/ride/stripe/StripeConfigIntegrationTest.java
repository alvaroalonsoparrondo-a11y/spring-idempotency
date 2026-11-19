package io.github.idempotency.ride.stripe;

import com.stripe.StripeClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StripeConfigIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(StripeConfig.class);

    @Test
    @DisplayName("IT context when only the api key is set should bind the defaults and build the client")
    void context_whenOnlyApiKeyIsSet_shouldBindDefaultsAndBuildClient() {
        runner.withPropertyValues("stripe.api-key=sk_test_123").run(context -> {
            // then
            assertThat(context).hasSingleBean(StripeClient.class);
            assertThat(context.getBean(StripeProperties.class)).isEqualTo(new StripeProperties(
                    "sk_test_123",
                    "https://api.stripe.com",
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(20),
                    2
            ));
        });
    }

    @Test
    @DisplayName("IT context when every property is set should bind each from its key")
    void context_whenEveryPropertyIsSet_shouldBindEachFromItsKey() {
        runner.withPropertyValues(
                "stripe.api-key=sk_test_123",
                "stripe.api-base=http://localhost:12111",
                "stripe.connect-timeout=1s",
                "stripe.read-timeout=3s",
                "stripe.max-network-retries=0"
        ).run(context -> {
            // then
            assertThat(context.getBean(StripeProperties.class)).isEqualTo(new StripeProperties(
                    "sk_test_123",
                    "http://localhost:12111",
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(3),
                    0
            ));
        });
    }

    @Test
    @DisplayName("IT context when the api key is missing should fail to start naming the property")
    void context_whenApiKeyIsMissing_shouldFailToStartNamingProperty() {
        runner.run(context -> {
            // then
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("apiKey");
        });
    }

    @Test
    @DisplayName("IT context when the retries are negative should fail to start naming the property")
    void context_whenRetriesAreNegative_shouldFailToStartNamingProperty() {
        runner.withPropertyValues("stripe.api-key=sk_test_123", "stripe.max-network-retries=-1").run(context -> {
            // then
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("maxNetworkRetries");
        });
    }

    @Test
    @DisplayName("IT context when a timeout is zero should fail to start, since the SDK reads zero as no timeout")
    void context_whenTimeoutIsZero_shouldFailToStart() {
        runner.withPropertyValues("stripe.api-key=sk_test_123", "stripe.read-timeout=0s").run(context -> {
            // then
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("readTimeout");
        });
    }
}
