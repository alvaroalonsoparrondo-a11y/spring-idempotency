package io.github.idempotency.ride.e2e.support;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * A thin HTTP client of the ride API, the way a mobile app would call it.
 */
public class RidesClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private final int port;

    RidesClient(int port) {
        this.baseUrl = "http://localhost:%d".formatted(port);
        this.port = port;
    }

    /**
     * Sends the whole booking request and closes the connection without reading the answer, the way a client that
     * gave up waiting leaves a request the server has received and goes on processing.
     */
    public void bookRideAndHangUp(String user, String idempotencyKey, String body) {
        byte [] content = body.getBytes(StandardCharsets.UTF_8);
        String head = ("POST /api/v1/rides HTTP/1.1\r\nHost: localhost:%d\r\nContent-Type: application/json\r\n"
                + "Authorization: %s\r\nIdempotency-Key: %s\r\nContent-Length: %d\r\nConnection: close\r\n\r\n")
                .formatted(port, user, idempotencyKey, content.length);
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(content);
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public HttpResponse<String> bookRide(String user, String idempotencyKey, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/rides"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (user != null) {
            request.header("Authorization", user);
        }
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
