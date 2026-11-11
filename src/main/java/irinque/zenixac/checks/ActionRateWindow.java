package irinque.zenixac.checks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

final class ActionRateWindow {
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final Map<Key, Window> windows = new ConcurrentHashMap<>();

    Result record(UUID playerId, String action, long now, int maximumPerSecond) {
        Window window = windows.computeIfAbsent(new Key(playerId, action), ignored -> new Window(now));
        synchronized (window) {
            if (now - window.startedAt >= WINDOW_NANOS) {
                window.startedAt = now;
                window.count = 0;
                window.reported = false;
            }
            window.count++;
            boolean exceeded = window.count > maximumPerSecond && !window.reported;
            if (exceeded) {
                window.reported = true;
            }
            return new Result(window.count, exceeded);
        }
    }

    void clear(UUID playerId) {
        windows.keySet().removeIf(key -> key.playerId.equals(playerId));
    }

    record Result(int count, boolean exceeded) {
    }

    private record Key(UUID playerId, String action) {
    }

    private static final class Window {
        private long startedAt;
        private int count;
        private boolean reported;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
