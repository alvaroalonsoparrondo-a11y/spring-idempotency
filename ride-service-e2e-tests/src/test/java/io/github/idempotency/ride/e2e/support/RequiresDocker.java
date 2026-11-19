package io.github.idempotency.ride.e2e.support;

import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnabledIf("io.github.idempotency.ride.e2e.support.Docker#availableOrCi")
public @interface RequiresDocker {
}
