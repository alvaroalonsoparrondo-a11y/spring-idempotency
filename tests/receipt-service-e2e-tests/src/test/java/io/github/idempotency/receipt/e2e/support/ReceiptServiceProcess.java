package io.github.idempotency.receipt.e2e.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;

/**
 * receipt-service started from its executable jar in a process of its own, configured through its environment
 * variables the way a container runs it.
 */
final class ReceiptServiceProcess {

    private static final Duration STARTUP = Duration.ofSeconds(90);

    private final Process process;
    private final Path log;

    private ReceiptServiceProcess(Process process, Path log) {
        this.process = process;
        this.log = log;
    }

    static ReceiptServiceProcess start(Map<String, String> environment) {
        Path jar = Path.of(System.getProperty("receiptService.jar", "target/apps/receipt-service.jar"));
        if (!Files.exists(jar)) {
            throw new IllegalStateException("No receipt-service jar at %s: build the reactor with verify".formatted(jar));
        }
        Path log = jar.resolveSibling("receipt-service.log");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(List.of(java, "-jar", jar.toString()))
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        builder.environment().putAll(environment);
        try {
            ReceiptServiceProcess started = new ReceiptServiceProcess(builder.start(), log);
            started.awaitStarted();
            Runtime.getRuntime().addShutdownHook(new Thread(started::stop));
            return started;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void awaitStarted() {
        await().atMost(STARTUP).pollInterval(Duration.ofMillis(500)).until(() -> {
            if (!process.isAlive()) {
                throw new IllegalStateException("receipt-service exited during startup, see " + log);
            }
            return Files.exists(log) && Files.readString(log).contains("Started ReceiptServiceApplication");
        });
    }

    void stop() {
        process.destroy();
    }
}
