package io.github.idempotency.simulator;

import org.springframework.stereotype.Component;

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
import java.util.Objects;
import java.util.UUID;

/**
 * Books a ride over HTTP, or sends the booking and hangs up before its answer: the failure a client on a bad network
 * sees, where the request reached the server and the answer never reached the client.
 */
@Component
public class RideClient {

    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final URI rides;

    public RideClient(SimulatorProperties properties) {
        Objects.requireNonNull(properties, "properties cannot be null");
        this.rides = URI.create(properties.baseUrl() + "/api/v1/rides");
    }

    public BookingAnswer book(String rider, UUID idempotencyKey, String body) {
        HttpRequest request = HttpRequest.newBuilder(rides)
                .timeout(ANSWER_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", rider)
                .header("Idempotency-Key", idempotencyKey.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new BookingAnswer(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while booking", e);
        }
    }

    /**
     * Writes the whole booking request and closes the connection without reading a byte of the answer.
     */
    public void bookAndHangUp(String rider, UUID idempotencyKey, String body) {
        byte [] content = body.getBytes(StandardCharsets.UTF_8);
        int port = rides.getPort() != -1 ? rides.getPort() : 80;
        String head = ("POST %s HTTP/1.1\r\nHost: %s:%d\r\nContent-Type: application/json\r\nAuthorization: %s\r\n"
                + "Idempotency-Key: %s\r\nContent-Length: %d\r\nConnection: close\r\n\r\n")
                .formatted(rides.getPath(), rides.getHost(), port, rider, idempotencyKey, content.length);
        try (Socket socket = new Socket(rides.getHost(), port)) {
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(content);
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
