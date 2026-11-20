package io.github.idempotency.ride.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.idempotency.contract.LogFields;
import io.github.idempotency.ride.user.User;
import io.github.idempotency.ride.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the {@link Caller} of a request: the user named by email in the Authorization header and the address
 * the request came from. A request naming no known user is answered 401 here and goes no further.
 * <p>
 * Runs ahead of the filter that replays recorded responses, so a repeated request is answered only to a known user.
 */
public class CallerResolvingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CallerResolvingFilter.class);

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CallerResolvingFilter(UserRepository userRepository, ObjectMapper objectMapper, Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<User> user = resolveUser(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (user.isEmpty()) {
            log.debug("Unauthorized request to {}", request.getRequestURI());
            writeProblem(response, Problems.unauthorized(request.getRequestURI(), clock.instant()));
            return;
        }
        request.setAttribute(Caller.ATTRIBUTE, new Caller(user.get(), request.getRemoteAddr()));
        MDC.put(LogFields.USER_ID, String.valueOf(user.get().id()));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(LogFields.USER_ID);
        }
    }

    private Optional<User> resolveUser(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(authorization.strip());
    }

    private void writeProblem(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
