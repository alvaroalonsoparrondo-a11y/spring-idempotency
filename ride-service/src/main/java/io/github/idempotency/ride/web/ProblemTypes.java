package io.github.idempotency.ride.web;

import java.net.URI;

/**
 * The {@code type} URIs of the problems the service answers with. A type is the only field a client may branch on,
 * so renaming one breaks every client that does.
 * <p>
 * The refusals of the idempotency layer itself - a missing or malformed key, a request still in flight, a key reused
 * with another body - carry idempify's own types, under {@code https://idempify.io/errors/}.
 */
public final class ProblemTypes {

    private static final String BASE = "https://dmitriy-iliyov.github.io/spring-idempotency/errors/";

    public static final URI UNAUTHORIZED = URI.create(BASE + "unauthorized");

    public static final URI VALIDATION_FAILED = URI.create(BASE + "validation-failed");

    public static final URI PAYMENT_DECLINED = URI.create(BASE + "payment-declined");

    public static final URI PAYMENT_FAILED = URI.create(BASE + "payment-failed");

    public static final URI PAYMENT_PROVIDER_UNAVAILABLE = URI.create(BASE + "payment-provider-unavailable");

    public static final URI BOOKING_EXPIRED = URI.create(BASE + "booking-expired");

    public static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");

    private ProblemTypes() {}
}
