package io.github.idempotency.ride.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.idempotency.ride.user.User;
import io.github.idempotency.ride.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallerResolvingFilterUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:15:30Z");
    private static final User USER = new User(3L, "rider@example.com", "cus_rider");

    // the way Spring Boot configures the application's mapper: dates as ISO strings
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    private CallerResolvingFilter tested;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        tested = new CallerResolvingFilter(userRepository, objectMapper, clock);
        request = new MockHttpServletRequest("POST", "/api/v1/rides");
        request.setRemoteAddr("203.0.113.7");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    @DisplayName("UT doFilter() when the header names a known user should pass the caller on to the chain")
    void doFilter_whenHeaderNamesKnownUser_shouldPassCallerOnToChain() throws Exception {
        // given
        request.addHeader(HttpHeaders.AUTHORIZATION, "rider@example.com");
        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(USER));

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(request.getAttribute(Caller.ATTRIBUTE)).isEqualTo(new Caller(USER, "203.0.113.7"));
    }

    @Test
    @DisplayName("UT doFilter() when the header has surrounding blanks should look the user up without them")
    void doFilter_whenHeaderHasSurroundingBlanks_shouldLookUserUpWithoutThem() throws Exception {
        // given
        request.addHeader(HttpHeaders.AUTHORIZATION, "  rider@example.com ");
        when(userRepository.findByEmail("rider@example.com")).thenReturn(Optional.of(USER));

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("UT doFilter() when the header names an unknown user should answer 401 and stop the chain")
    void doFilter_whenHeaderNamesUnknownUser_shouldAnswerUnauthorizedAndStopChain() throws Exception {
        // given
        request.addHeader(HttpHeaders.AUTHORIZATION, "stranger@example.com");
        when(userRepository.findByEmail("stranger@example.com")).thenReturn(Optional.empty());

        // when
        tested.doFilter(request, response, chain);

        // then
        assertUnauthorized();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("UT doFilter() when the header is missing or blank should answer 401 without a lookup")
    void doFilter_whenHeaderIsMissingOrBlank_shouldAnswerUnauthorizedWithoutLookup(String authorization)
            throws Exception {
        // given
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }

        // when
        tested.doFilter(request, response, chain);

        // then
        assertUnauthorized();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("UT constructor when userRepository is null should throw NullPointerException")
    void constructor_whenUserRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new CallerResolvingFilter(null, objectMapper, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userRepository cannot be null");
    }

    @Test
    @DisplayName("UT constructor when objectMapper is null should throw NullPointerException")
    void constructor_whenObjectMapperIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new CallerResolvingFilter(userRepository, null, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("objectMapper cannot be null");
    }

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new CallerResolvingFilter(userRepository, objectMapper, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock cannot be null");
    }

    private void assertUnauthorized() throws Exception {
        assertThat(chain.getRequest()).isNull();
        assertThat(request.getAttribute(Caller.ATTRIBUTE)).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("type").asText()).isEqualTo(ProblemTypes.UNAUTHORIZED.toString());
        assertThat(body.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isEqualTo("The Authorization header does not name a known user.");
        assertThat(body.get("instance").asText()).isEqualTo("/api/v1/rides");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/rides");
        assertThat(body.get("timestamp").asText()).isEqualTo(NOW.toString());
    }
}
