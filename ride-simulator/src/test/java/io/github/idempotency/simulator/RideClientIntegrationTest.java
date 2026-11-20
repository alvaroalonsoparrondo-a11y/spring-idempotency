package io.github.idempotency.simulator;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against a local HTTP server standing in for ride-service.
 */
class RideClientIntegrationTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    private final Map<String, String> received = new ConcurrentHashMap<>();
    private final CountDownLatch handled = new CountDownLatch(1);
    private HttpServer server;
    private RideClient tested;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/rides", exchange -> {
            received.put("method", exchange.getRequestMethod());
            received.put("authorization", exchange.getRequestHeaders().getFirst("Authorization"));
            received.put("key", exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            received.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            handled.countDown();
            byte [] answer = "{\"id\":7}".getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = exchange.getResponseBody()) {
                exchange.sendResponseHeaders(201, answer.length);
                out.write(answer);
            } catch (IOException e) {
                // the client hung up before the answer, which is the case some tests make
            }
        });
        server.start();
        tested = new RideClient(new SimulatorProperties(
                "http://localhost:%d".formatted(server.getAddress().getPort()),
                List.of("rider@example.com")
        ));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("IT book() should post the ride under the rider and the key and return the answer")
    void book_whenServerAnswers_shouldPostRideUnderRiderAndKeyAndReturnAnswer() {
        // when
        BookingAnswer answer = tested.book("rider@example.com", KEY, "{\"origin\":{}}");

        // then
        assertThat(answer).isEqualTo(new BookingAnswer(201, "{\"id\":7}"));
        assertThat(received).containsEntry("method", "POST")
                .containsEntry("authorization", "rider@example.com")
                .containsEntry("key", KEY.toString())
                .containsEntry("body", "{\"origin\":{}}");
    }

    @Test
    @DisplayName("IT bookAndHangUp() should deliver the whole request before it hangs up")
    void bookAndHangUp_whenCalled_shouldDeliverWholeRequestBeforeItHangsUp() throws Exception {
        // when
        tested.bookAndHangUp("rider@example.com", KEY, "{\"origin\":{\"lat\":1}}");

        // then
        assertThat(handled.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).containsEntry("method", "POST")
                .containsEntry("authorization", "rider@example.com")
                .containsEntry("key", KEY.toString())
                .containsEntry("body", "{\"origin\":{\"lat\":1}}");
    }
}
