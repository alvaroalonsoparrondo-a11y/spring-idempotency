package io.github.idempotency.ride.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single table of "what went wrong" to status, {@link ProblemTypes type} and title.
 */
final class Problems {

    private static final String PATH = "path";
    private static final String TIMESTAMP = "timestamp";
    private static final String ERRORS = "errors";

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

    static ProblemDetail validationFailed(List<FieldError> fieldErrors, String instance, Instant timestamp) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.VALIDATION_FAILED,
                "Request validation failed",
                "The request body has invalid fields.",
                instance,
                timestamp
        );
        problem.setProperty(ERRORS, fieldErrors.stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList());
        return problem;
    }

    static ProblemDetail paymentDeclined(String detail, String instance, Instant timestamp) {
        return problem(
                HttpStatus.PAYMENT_REQUIRED,
                ProblemTypes.PAYMENT_DECLINED,
                "Payment declined",
                detail,
                instance,
                timestamp
        );
    }

    static ProblemDetail paymentProviderUnavailable(String instance, Instant timestamp) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                ProblemTypes.PAYMENT_PROVIDER_UNAVAILABLE,
                "Payment provider unavailable",
                "The payment could not be completed. Retry the request with the same Idempotency-Key.",
                instance,
                timestamp
        );
    }

    static ProblemDetail idempotencyKeyReused(String detail, String instance, Instant timestamp) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                io.github.dmitriyiliyov.idempify.http.ProblemTypes.IDEMPOTENCY_KEY_REUSE,
                "Idempotency-Key reuse",
                detail,
                instance,
                timestamp
        );
    }

    static ProblemDetail internalError(String instance, Instant timestamp) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.INTERNAL_ERROR,
                "Internal error",
                "The operation could not be completed.",
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
