package io.github.idempotency.ride.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonBodyGuardFilterUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:15:30Z");
    private static final String BODY = "{\"origin\": {\"lat\": 52.52, \"lon\": 13.405}}";

    // the way Spring Boot configures the application's mapper: dates as ISO strings
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JsonBodyGuardFilter tested = new JsonBodyGuardFilter(objectMapper, clock);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    @DisplayName("UT doFilter() when the body is JSON should hand the same body on to the chain")
    void doFilter_whenBodyIsJson_shouldHandSameBodyOnToChain() throws Exception {
        // given
        MockHttpServletRequest request = request("POST", BODY);

        // when
        tested.doFilter(request, response, chain);

        // then
        byte[] handedOn = chain.getRequest().getInputStream().readAllBytes();
        assertThat(new String(handedOn, StandardCharsets.UTF_8)).isEqualTo(BODY);
        assertThat(chain.getRequest().getContentLength()).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8).length);
        assertThat(chain.getRequest().getReader().readLine()).isEqualTo(BODY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{not json", "{\"origin\": ", "hello"})
    @DisplayName("UT doFilter() when the body is empty or not JSON should answer Spring's 400 and stop the chain")
    void doFilter_whenBodyIsEmptyOrNotJson_shouldAnswerSpringBadRequestAndStopChain(String body) throws Exception {
        // given
        MockHttpServletRequest request = request("PATCH", body);

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode problem = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(problem.get("type").asText()).isEqualTo("about:blank");
        assertThat(problem.get("title").asText()).isEqualTo("Bad Request");
        assertThat(problem.get("detail").asText()).isEqualTo("Failed to read request");
        assertThat(problem.get("path").asText()).isEqualTo("/api/v1/rides");
        assertThat(problem.get("timestamp").asText()).isEqualTo(NOW.toString());
    }

    @Test
    @DisplayName("UT doFilter() when the request is not a write should pass it on without reading the body")
    void doFilter_whenRequestIsNotWrite_shouldPassItOnWithoutReadingBody() throws Exception {
        // given
        MockHttpServletRequest request = request("GET", "{not json");

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("UT constructor when objectMapper is null should throw NullPointerException")
    void constructor_whenObjectMapperIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new JsonBodyGuardFilter(null, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("objectMapper cannot be null");
    }

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new JsonBodyGuardFilter(objectMapper, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock cannot be null");
    }

    private static MockHttpServletRequest request(String method, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/rides");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
