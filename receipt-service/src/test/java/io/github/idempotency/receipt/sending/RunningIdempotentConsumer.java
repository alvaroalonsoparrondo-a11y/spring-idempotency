package io.github.idempotency.receipt.sending;

import io.github.dmitriyiliyov.oncebox.core.inbox.EventIdExtractor;
import io.github.dmitriyiliyov.oncebox.core.inbox.IdempotentConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Runs every operation it is handed and records the event ids it extracted, as an inbox that has seen none of them.
 */
class RunningIdempotentConsumer implements IdempotentConsumer {

    private final List<UUID> consumedIds = new ArrayList<>();

    List<UUID> consumedIds() {
        return consumedIds;
    }

    @Override
    public void consume(UUID eventId, Runnable operation) {
        consumedIds.add(eventId);
        operation.run();
    }

    @Override
    public <T> void consume(T message, EventIdExtractor<T> idExtractor, Consumer<T> operation) {
        consumedIds.add(idExtractor.extract(message));
        operation.accept(message);
    }

    @Override
    public void consume(Set<UUID> ids, Consumer<Set<UUID>> operation) {
        consumedIds.addAll(ids);
        operation.accept(ids);
    }

    @Override
    public <T> void consume(List<T> messages, EventIdExtractor<T> idExtractor, Consumer<List<T>> operation) {
        messages.forEach(message -> consumedIds.add(idExtractor.extract(message)));
        operation.accept(messages);
    }
}
