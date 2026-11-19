package io.github.idempotency.ride.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the request's {@code Idempotency-Key} to a {@link java.util.UUID} parameter, read the way idempify reads
 * it: the first value of the header, in canonical form. A missing or malformed key is refused with idempify's own
 * problem before the handler is called, so the operation is never claimed under a key the handler would not get.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotencyKey {
}
