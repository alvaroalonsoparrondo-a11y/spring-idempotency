package io.github.idempotency.ride.web;

import io.github.idempotency.contract.LogFields;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Gives every request a correlation id - the caller's {@code X-Correlation-Id} when it sends a usable one, a new one
 * otherwise - and puts it, with the request's Idempotency-Key, on every log line written while the request runs.
 * The id is echoed in the response, so a client can quote it.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /**
     * What a caller's id may look like before it is written into the logs: short, without spaces or control
     * characters that would let it forge a log line.
     */
    private static final Pattern USABLE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private final Supplier<String> idGenerator;

    public CorrelationIdFilter() {
        this(() -> UUID.randomUUID().toString());
    }

    CorrelationIdFilter(Supplier<String> idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = usable(request.getHeader(HEADER)) ? request.getHeader(HEADER) : idGenerator.get();
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        MDC.put(LogFields.CORRELATION_ID, correlationId);
        if (usable(idempotencyKey)) {
            MDC.put(LogFields.IDEMPOTENCY_KEY, idempotencyKey);
        }
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(LogFields.CORRELATION_ID);
            MDC.remove(LogFields.IDEMPOTENCY_KEY);
            MDC.remove(LogFields.USER_ID);
        }
    }

    private static boolean usable(String value) {
        return value != null && USABLE.matcher(value).matches();
    }
}
