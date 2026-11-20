package io.github.idempotency.ride.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterUnitTest {

    private static final String GENERATED = "generated-id";
    private static final String KEY = "aaaaaaaa-0000-4000-8000-000000000001";

    private final CorrelationIdFilter tested = new CorrelationIdFilter(() -> GENERATED);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rides");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final Map<String, String> seenByChain = new HashMap<>();
    private final FilterChain chain = (req, res) -> seenByChain.putAll(MDC.getCopyOfContextMap());

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("UT doFilter() when the caller sends no correlation id should log and answer under a new one")
    void doFilter_whenCallerSendsNoCorrelationId_shouldLogAndAnswerUnderNewOne() throws Exception {
        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(seenByChain).containsEntry("correlationId", GENERATED);
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(GENERATED);
    }

    @Test
    @DisplayName("UT doFilter() when the caller sends a usable correlation id should keep it")
    void doFilter_whenCallerSendsUsableCorrelationId_shouldKeepIt() throws Exception {
        // given
        request.addHeader("X-Correlation-Id", "mobile-4.2.0:7f3a");

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(seenByChain).containsEntry("correlationId", "mobile-4.2.0:7f3a");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("mobile-4.2.0:7f3a");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "two words", "line\nbreak", "{\"forged\":true}"})
    @DisplayName("UT doFilter() when the caller's correlation id could forge a log line should replace it")
    void doFilter_whenCallerCorrelationIdCouldForgeLogLine_shouldReplaceIt(String sent) throws Exception {
        // given
        request.addHeader("X-Correlation-Id", sent);

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(seenByChain).containsEntry("correlationId", GENERATED);
    }

    @Test
    @DisplayName("UT doFilter() when the caller's correlation id is longer than 64 characters should replace it")
    void doFilter_whenCallerCorrelationIdIsLongerThan64Characters_shouldReplaceIt() throws Exception {
        // given
        request.addHeader("X-Correlation-Id", "a".repeat(65));

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(seenByChain).containsEntry("correlationId", GENERATED);
    }

    @Test
    @DisplayName("UT doFilter() when the request carries an Idempotency-Key should log it with the request")
    void doFilter_whenRequestCarriesIdempotencyKey_shouldLogItWithRequest() throws Exception {
        // given
        request.addHeader("Idempotency-Key", KEY);

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(seenByChain).containsEntry("idempotencyKey", KEY);
    }

    @Test
    @DisplayName("UT doFilter() when the Idempotency-Key could forge a log line should leave it out of the logs")
    void doFilter_whenIdempotencyKeyCouldForgeLogLine_shouldLeaveItOutOfLogs() throws Exception {
        // given
        request.addHeader("Idempotency-Key", "key\nwith break");

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(seenByChain).doesNotContainKey("idempotencyKey");
    }

    @Test
    @DisplayName("UT doFilter() when the request is done should leave nothing of it in the thread's log context")
    void doFilter_whenRequestIsDone_shouldLeaveNothingInThreadLogContext() throws Exception {
        // given
        request.addHeader("Idempotency-Key", KEY);
        MDC.put("userId", "3");

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("UT constructor without a generator should make a UUID per request")
    void constructor_withoutGenerator_shouldMakeUuidPerRequest() throws Exception {
        // when
        new CorrelationIdFilter().doFilter(request, response, chain);

        // then
        assertThat(seenByChain.get("correlationId")).matches("[0-9a-f-]{36}");
    }
}
