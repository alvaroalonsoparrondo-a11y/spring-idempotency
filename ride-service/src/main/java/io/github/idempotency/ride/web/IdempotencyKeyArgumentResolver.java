package io.github.idempotency.ride.web;

import io.github.dmitriyiliyov.idempify.core.IdempotencyKeyException;
import io.github.dmitriyiliyov.idempify.core.UuidUtils;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Resolves {@link IdempotencyKey} parameters. The messages are idempify's own, so the refusal reads the same
 * whichever of the two checks it.
 */
public class IdempotencyKeyArgumentResolver implements HandlerMethodArgumentResolver {

    static final String HEADER = "Idempotency-Key";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(IdempotencyKey.class) && UUID.class == parameter.getParameterType();
    }

    @Override
    public UUID resolveArgument(MethodParameter parameter,
                                ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest,
                                WebDataBinderFactory binderFactory) {
        String raw = webRequest.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            throw new IdempotencyKeyException("HTTP header %s is null or empty".formatted(HEADER));
        }
        UUID key = UuidUtils.parseCanonical(raw);
        if (key == null) {
            throw new IdempotencyKeyException("HTTP header %s value '%s' is not a valid UUID".formatted(HEADER, raw));
        }
        return key;
    }
}
