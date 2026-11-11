package irinque.zenixac.storage;

import irinque.zenixac.model.PlayerProfile;
import irinque.zenixac.model.PlayerSession;
import irinque.zenixac.model.ViolationRecord;
import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ViolationService implements AutoCloseable {
    private final ViolationStore store;
    private final int retentionDays;
    private final ThreadPoolExecutor executor;
    private final Logger logger;
    private final LongAdder acceptedEvents = new LongAdder();
    private final LongAdder droppedEvents = new LongAdder();
    private volatile boolean storeReady;
    private volatile boolean stopping;
    private volatile long lastStorageWarningAt;
    private volatile long lastQueueWarningAt;

    public ViolationService(File databaseFile, int retentionDays, int queueCapacity, Logger logger) {
        this.store = new SqliteViolationStore(databaseFile);
        this.retentionDays = retentionDays;
        this.logger = logger;
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "ZenixAC-SQLite");
                    thread.setDaemon(true);
                    return thread;
                }
        );
        enqueue(() -> {
            try {
                store.initialize(retentionDays);
                storeReady = true;
                logger.info("[ZenixAC] SQLite history is ready");
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "[ZenixAC] History storage is unavailable; live checks remain active", exception);
            }
        }, false);
    }

    public void startSession(PlayerSession session) {
        enqueue(() -> {
            if (storeReady) {
                store.startSession(session);
            }
        }, false);
    }

    public void endSession(UUID sessionId, long endedAt) {
        enqueue(() -> {
            if (storeReady) {
                store.endSession(sessionId, endedAt);
            }
        }, false);
    }

    public void record(ViolationRecord record) {
        acceptedEvents.increment();
        enqueue(() -> {
            if (storeReady) {
                store.saveViolation(record);
            } else {
                droppedEvents.increment();
            }
        }, true);
    }

    public CompletableFuture<List<ViolationRecord>> recent(UUID playerId, int limit) {
        return submitQuery(() -> store.getRecent(playerId, limit));
    }

    public CompletableFuture<List<ViolationRecord>> recent(UUID playerId, UUID sessionId, int limit) {
        return submitQuery(() -> store.getRecent(playerId, sessionId, limit));
    }

    public CompletableFuture<List<ViolationRecord>> recent(String playerName, UUID sessionId, int limit) {
        return submitQuery(() -> store.getRecent(playerName, sessionId, limit));
    }

    public CompletableFuture<PlayerProfile> profile(UUID playerId) {
        return submitQuery(() -> store.getProfile(playerId));
    }

    public CompletableFuture<PlayerProfile> profile(String playerName) {
        return submitQuery(() -> store.getProfile(playerName));
    }

    public long getAcceptedEvents() {
        return acceptedEvents.sum();
    }

    public long getDroppedEvents() {
        return droppedEvents.sum();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    private <T> CompletableFuture<T> submitQuery(StoreQuery<T> query) {
        CompletableFuture<T> future = new CompletableFuture<>();
        enqueue(() -> {
            if (!storeReady) {
                future.completeExceptionally(new IllegalStateException("History storage is unavailable"));
                return;
            }
            try {
                future.complete(query.run());
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        }, false, future);
        return future;
    }

    private void enqueue(StoreTask task, boolean countAsDropped) {
        enqueue(task, countAsDropped, null);
    }

    private void enqueue(StoreTask task, boolean countAsDropped, CompletableFuture<?> future) {
        if (stopping) {
            if (countAsDropped) {
                droppedEvents.increment();
            }
            if (future != null) {
                future.completeExceptionally(new IllegalStateException("History storage is shutting down"));
            }
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception exception) {
                    if (countAsDropped) {
                        droppedEvents.increment();
                    }
                    warnStorageFailure(exception);
                    if (future != null) {
                        future.completeExceptionally(exception);
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            if (countAsDropped) {
                droppedEvents.increment();
            }
            if (future != null) {
                future.completeExceptionally(new IllegalStateException("History queue is full", exception));
            }
            if (countAsDropped) {
                warnQueueFull();
            }
        }
    }

    private synchronized void warnStorageFailure(Exception exception) {
        long now = System.currentTimeMillis();
        if (now - lastStorageWarningAt >= 60_000L) {
            lastStorageWarningAt = now;
            logger.log(Level.WARNING, "[ZenixAC] A history operation failed; live checks remain active", exception);
        }
    }

    private synchronized void warnQueueFull() {
        long now = System.currentTimeMillis();
        if (now - lastQueueWarningAt >= 60_000L) {
            lastQueueWarningAt = now;
            logger.warning("[ZenixAC] History queue is full; some violation records may not be saved");
        }
    }

    @Override
    public void close() {
        stopping = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                int pending = executor.shutdownNow().size();
                droppedEvents.add(pending);
                logger.warning("[ZenixAC] Storage stopped with " + pending + " queued operation(s) unsaved");
                executor.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            int pending = executor.shutdownNow().size();
            droppedEvents.add(pending);
            logger.log(Level.WARNING, "[ZenixAC] Interrupted while closing storage; queued operations may be unsaved", exception);
        }

        if (!executor.isTerminated()) {
            logger.warning("[ZenixAC] Storage worker did not stop; leaving its connection to the process shutdown");
            return;
        }

        try {
            store.close();
        } catch (Exception exception) {
            logger.log(Level.WARNING, "[ZenixAC] Could not close history storage cleanly", exception);
        }
    }

    @FunctionalInterface
    private interface StoreTask {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface StoreQuery<T> {
        T run() throws Exception;
    }
}
