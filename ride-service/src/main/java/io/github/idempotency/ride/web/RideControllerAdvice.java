package io.github.idempotency.ride.web;

import io.github.idempotency.ride.booking.IdempotencyKeyReusedException;
import io.github.idempotency.ride.booking.RideController;
import io.github.idempotency.ride.stripe.PaymentDeclinedException;
import io.github.idempotency.ride.stripe.PaymentProviderException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;
import java.util.Objects;

/**
 * Answers the failures of the ride endpoints as problem details. Ordered last, behind idempify's advice (see
 * {@link IdempifyAdviceConfig}), so the refusals of the idempotency layer keep idempify's answers and only the rest
 * reaches the catch-all here.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = RideController.class)
public class RideControllerAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RideControllerAdvice.class);

    private final Clock clock;

    public RideControllerAdvice(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    public ProblemDetail handlePaymentDeclinedException(PaymentDeclinedException exception,
                                                        HttpServletRequest request) {
        log.debug("Payment declined for {}", request.getRequestURI());
        return Problems.paymentDeclined(exception.getMessage(), request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ProblemDetail handlePaymentProviderException(PaymentProviderException exception,
                                                        HttpServletRequest request) {
        log.error("Payment provider failed for {}", request.getRequestURI(), exception);
        return Problems.paymentProviderUnavailable(request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ProblemDetail handleIdempotencyKeyReusedException(IdempotencyKeyReusedException exception,
                                                             HttpServletRequest request) {
        log.debug("Idempotency-Key reused for {}", request.getRequestURI());
        return Problems.idempotencyKeyReused(exception.getMessage(), request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception exception, HttpServletRequest request) {
        log.error("Request to {} failed", request.getRequestURI(), exception);
        return Problems.internalError(request.getRequestURI(), clock.instant());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String instance = ((ServletWebRequest) request).getRequest().getRequestURI();
        log.debug("Request validation failed for {}", instance);
        ProblemDetail problem = Problems.validationFailed(exception.getFieldErrors(), instance, clock.instant());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception,
                                                             @Nullable Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            Problems.stamp(problem, ((ServletWebRequest) request).getRequest().getRequestURI(), clock.instant());
        }
        return super.handleExceptionInternal(exception, body, headers, statusCode, request);
    }
}
