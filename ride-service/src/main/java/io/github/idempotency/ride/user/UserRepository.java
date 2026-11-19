package io.github.idempotency.ride.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    public Optional<User> findByEmail(String email) {
        return jdbc.sql("SELECT id, email, stripe_customer_id FROM users WHERE email = ?")
                .param(email)
                .query((rs, rowNum) -> new User(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("stripe_customer_id")
                ))
                .optional();
    }
}
