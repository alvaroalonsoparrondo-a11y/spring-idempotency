package io.github.idempotency.ride.reaper;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Deletes expired idempotency records on a schedule, a batch at a time so that no single statement holds many rows
 * locked, until a batch comes back short.
 */
@Component
public class IdempotencyRecordReaper {

    static final int BATCH_SIZE = 1000;

    private final IdempotencyRecordRepository repository;

    public IdempotencyRecordReaper(IdempotencyRecordRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    /**
     * @return how many records this run deleted
     */
    @Scheduled(initialDelay = 1, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public int reap() {
        int total = 0;
        int deleted;
        do {
            deleted = repository.deleteExpired(BATCH_SIZE);
            total += deleted;
        } while (deleted == BATCH_SIZE);
        return total;
    }
}
