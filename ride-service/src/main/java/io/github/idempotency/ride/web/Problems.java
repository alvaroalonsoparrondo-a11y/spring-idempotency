package io.github.idempotency.ride.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;

/**
 * The single table of "what went wrong" to status, {@link ProblemTypes type} and title.
 */
final class Problems {

    private static final String PATH = "path";
    private static final String TIMESTAMP = "timestamp";

    private Problems() {}

    static ProblemDetail unauthorized(String instance, Instant timestamp) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                ProblemTypes.UNAUTHORIZED,
                "Unauthorized",
                "The Authorization header does not name a known user.",
                instance,
                timestamp
        );
    }

    static void stamp(ProblemDetail problem, String instance, Instant timestamp) {
        problem.setInstance(URI.create(instance));
        problem.setProperty(PATH, instance);
        problem.setProperty(TIMESTAMP, timestamp);
    }

    private static ProblemDetail problem(HttpStatus status,
                                         URI type,
                                         String title,
                                         String detail,
                                         String instance,
                                         Instant timestamp) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        stamp(problem, instance, timestamp);
        return problem;
    }
}
