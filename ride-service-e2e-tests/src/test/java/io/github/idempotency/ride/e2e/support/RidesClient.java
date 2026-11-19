package io.github.idempotency.ride.e2e.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;

/**
 * A thin HTTP client of the ride API, the way a mobile app would call it.
 */
public class RidesClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;

    RidesClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public HttpResponse<String> bookRide(String user, String idempotencyKey, String body) {
        return bookRide(user, idempotencyKey, body, DEFAULT_TIMEOUT).orElseThrow();
    }

    /**
     * @return the answer, or empty when none came within the timeout - the request may still be running
     */
    public Optional<HttpResponse<String>> bookRide(String user, String idempotencyKey, String body, Duration timeout) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/rides"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (user != null) {
            request.header("Authorization", user);
        }
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        try {
            return Optional.of(http.send(request.build(), HttpResponse.BodyHandlers.ofString()));
        } catch (HttpTimeoutException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
