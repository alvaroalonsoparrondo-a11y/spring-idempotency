package io.github.idempotency.ride.web;

import java.net.URI;

/**
 * The {@code type} URIs of the problems the service answers with. A type is the only field a client may branch on,
 * so renaming one breaks every client that does.
 */
public final class ProblemTypes {

    private static final String BASE = "https://dmitriy-iliyov.github.io/spring-idempotency/errors/";

    public static final URI UNAUTHORIZED = URI.create(BASE + "unauthorized");

    private ProblemTypes() {}
}
