package io.github.idempotency.ride.support;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A transaction manager with no resource behind it, counting what it was asked to do, for unit tests of code that
 * runs its work through a {@link org.springframework.transaction.support.TransactionTemplate}.
 */
public class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    private final AtomicInteger commits = new AtomicInteger();
    private final AtomicInteger rollbacks = new AtomicInteger();

    public int commits() {
        return commits.get();
    }

    public int rollbacks() {
        return rollbacks.get();
    }

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        commits.incrementAndGet();
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        rollbacks.incrementAndGet();
    }
}
