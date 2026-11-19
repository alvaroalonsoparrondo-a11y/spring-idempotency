package io.github.idempotency.ride.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;

/**
 * Answers a write request whose body is empty or not JSON with the 400 Spring MVC gives an unreadable body, before
 * idempify's filter sees it. idempify fingerprints the body ahead of the {@code DispatcherServlet} and answers a body
 * it cannot canonicalise with a 500, as if its own fingerprint policy were broken.
 * <p>
 * The body is read once and handed on to the rest of the chain unchanged.
 */
public class JsonBodyGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JsonBodyGuardFilter.class);
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH");

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JsonBodyGuardFilter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WRITE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        if (!isJson(body)) {
            log.debug("Unreadable request body for {}", request.getRequestURI());
            writeProblem(response, Problems.unreadableBody(request.getRequestURI(), clock.instant()));
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private boolean isJson(byte[] body) {
        if (body.length == 0) {
            return false;
        }
        try {
            return objectMapper.readTree(body) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private void writeProblem(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {

                @Override
                public boolean isFinished() {
                    return stream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("The body is already read");
                }

                @Override
                public int read() {
                    return stream.read();
                }

                @Override
                public int read(byte [] buffer, int offset, int length) {
                    return stream.read(buffer, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(
                    getInputStream(),
                    encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8
            ));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
