package io.github.idempotency.ride.user;

import io.github.idempotency.ride.support.RequiresDocker;
import io.github.idempotency.ride.support.TestImages;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@RequiresDocker
class UserRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TestImages.POSTGRES);

    private static JdbcClient jdbc;

    private final UserRepository tested = new UserRepository(jdbc);

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE audit_records, rides, users RESTART IDENTITY CASCADE").update();
    }

    @Test
    @DisplayName("IT findByEmail() when the user exists should return them with their Stripe customer")
    void findByEmail_whenUserExists_shouldReturnThemWithStripeCustomer() {
        // given
        long id = jdbc.sql("INSERT INTO users (email, stripe_customer_id) VALUES (?, ?) RETURNING id")
                .params("rider@example.com", "cus_rider")
                .query(Long.class)
                .single();

        // when
        Optional<User> found = tested.findByEmail("rider@example.com");

        // then
        assertThat(found).contains(new User(id, "rider@example.com", "cus_rider"));
    }

    @Test
    @DisplayName("IT findByEmail() when no user has the email should return empty")
    void findByEmail_whenNoUserHasEmail_shouldReturnEmpty() {
        // when
        Optional<User> found = tested.findByEmail("stranger@example.com");

        // then
        assertThat(found).isEmpty();
    }
}
