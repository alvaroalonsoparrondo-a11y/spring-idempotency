package io.github.idempotency.ride.web;

import io.github.idempotency.ride.user.User;

import java.util.Objects;

/**
 * Who sent the request and from where, resolved by {@link CallerResolvingFilter} before the request reaches a
 * controller, which takes it as {@code @RequestAttribute(Caller.ATTRIBUTE)}.
 *
 * @param user     the user the Authorization header named
 * @param originIp the address the request came from, after the proxies the server trusts
 */
public record Caller(User user, String originIp) {

    public static final String ATTRIBUTE = "io.github.idempotency.ride.web.Caller";

    public Caller {
        Objects.requireNonNull(user, "user cannot be null");
        Objects.requireNonNull(originIp, "originIp cannot be null");
    }
}
