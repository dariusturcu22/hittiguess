package org.dariusturcu.backend.pipeline;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tells this process's stage dispatcher that jobs of a stage became available, so it leases them
 * without waiting for its next poll. A signal raised inside a transaction is delivered only after
 * that transaction commits, because the jobs are invisible to other connections before then.
 * Signals are hints: with no dispatcher registered they are dropped, and the poll still finds
 * the work.
 */
@Component
public class StageWakeUps {

    public interface Listener {
        void wake(PipelineStage stage);

        void wakeAt(PipelineStage stage, Instant dueAt);
    }

    private final AtomicReference<Listener> listener = new AtomicReference<>();

    public void register(Listener stageListener) {
        listener.set(stageListener);
    }

    public void unregister(Listener stageListener) {
        listener.compareAndSet(stageListener, null);
    }

    public void wake(PipelineStage stage) {
        afterCommit(() -> {
            Listener current = listener.get();
            if (current != null) {
                current.wake(stage);
            }
        });
    }

    public void wakeAt(PipelineStage stage, Instant dueAt) {
        afterCommit(() -> {
            Listener current = listener.get();
            if (current != null) {
                current.wakeAt(stage, dueAt);
            }
        });
    }

    private static void afterCommit(Runnable signal) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            signal.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                signal.run();
            }
        });
    }
}
