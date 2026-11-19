package io.github.idempotency.ride.web;

import io.github.dmitriyiliyov.idempify.core.IdempotencyKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyArgumentResolverUnitTest {

    private static final String KEY = "aaaaaaaa-0000-4000-8000-000000000001";

    private final IdempotencyKeyArgumentResolver tested = new IdempotencyKeyArgumentResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rides");

    @Test
    @DisplayName("UT supportsParameter() when a UUID parameter carries the annotation should support it")
    void supportsParameter_whenUuidParameterCarriesAnnotation_shouldSupportIt() throws Exception {
        // when / then
        assertThat(tested.supportsParameter(parameter(0))).isTrue();
    }

    @Test
    @DisplayName("UT supportsParameter() when the annotated parameter is not a UUID should not support it")
    void supportsParameter_whenAnnotatedParameterIsNotUuid_shouldNotSupportIt() throws Exception {
        // when / then
        assertThat(tested.supportsParameter(parameter(1))).isFalse();
    }

    @Test
    @DisplayName("UT supportsParameter() when a UUID parameter has no annotation should not support it")
    void supportsParameter_whenUuidParameterHasNoAnnotation_shouldNotSupportIt() throws Exception {
        // when / then
        assertThat(tested.supportsParameter(parameter(2))).isFalse();
    }

    @Test
    @DisplayName("UT resolveArgument() when the header holds a canonical UUID should return it")
    void resolveArgument_whenHeaderHoldsCanonicalUuid_shouldReturnIt() throws Exception {
        // given
        request.addHeader("Idempotency-Key", KEY);

        // when
        UUID key = resolve();

        // then
        assertThat(key).isEqualTo(UUID.fromString(KEY));
    }

    @Test
    @DisplayName("UT resolveArgument() when the header is sent twice should take the first value, as idempify does")
    void resolveArgument_whenHeaderIsSentTwice_shouldTakeFirstValueAsIdempifyDoes() throws Exception {
        // given
        request.addHeader("Idempotency-Key", KEY);
        request.addHeader("Idempotency-Key", UUID.randomUUID().toString());

        // when
        UUID key = resolve();

        // then
        assertThat(key).isEqualTo(UUID.fromString(KEY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    @DisplayName("UT resolveArgument() when the header is blank should throw IdempotencyKeyException")
    void resolveArgument_whenHeaderIsBlank_shouldThrowIdempotencyKeyException(String value) {
        // given
        request.addHeader("Idempotency-Key", value);

        // when / then
        assertThatThrownBy(this::resolve)
                .isInstanceOf(IdempotencyKeyException.class)
                .hasMessage("HTTP header Idempotency-Key is null or empty");
    }

    @Test
    @DisplayName("UT resolveArgument() when the header is missing should throw IdempotencyKeyException")
    void resolveArgument_whenHeaderIsMissing_shouldThrowIdempotencyKeyException() {
        // when / then
        assertThatThrownBy(this::resolve)
                .isInstanceOf(IdempotencyKeyException.class)
                .hasMessage("HTTP header Idempotency-Key is null or empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ride-123", "1-1-1-1-1", "aaaaaaaa00004000800000000000001"})
    @DisplayName("UT resolveArgument() when the header is not a canonical UUID should throw IdempotencyKeyException")
    void resolveArgument_whenHeaderIsNotCanonicalUuid_shouldThrowIdempotencyKeyException(String value) {
        // given
        request.addHeader("Idempotency-Key", value);

        // when / then
        assertThatThrownBy(this::resolve)
                .isInstanceOf(IdempotencyKeyException.class)
                .hasMessage("HTTP header Idempotency-Key value '%s' is not a valid UUID".formatted(value));
    }

    private UUID resolve() throws Exception {
        return tested.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);
    }

    private static MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = Handlers.class.getDeclaredMethod("handle", UUID.class, String.class, UUID.class);
        return new MethodParameter(method, index);
    }

    static class Handlers {

        void handle(@IdempotencyKey UUID key, @IdempotencyKey String notUuid, UUID notAnnotated) {
        }
    }
}
