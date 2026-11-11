package irinque.zenixac.checks;

import irinque.zenixac.ZenixAnticheat;
import irinque.zenixac.ZenixEvents;
import irinque.zenixac.config.ConfigurationManager;
import irinque.zenixac.model.ViolationRecord;
import irinque.zenixac.notification.StaffNotifier;
import irinque.zenixac.platform.ClientProtocolProvider;
import irinque.zenixac.session.SessionManager;
import irinque.zenixac.storage.ViolationService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class ViolationManager {
    private final Map<CheckKey, CheckState> levels = new ConcurrentHashMap<>();
    private final ConfigurationManager configurationManager;
    private final ZenixAnticheat plugin;
    private final ViolationService violationService;
    private final SessionManager sessionManager;
    private final ClientProtocolProvider clientProtocolProvider;
    private final StaffNotifier staffNotifier;

    public ViolationManager(
            ZenixAnticheat plugin,
            ConfigurationManager configurationManager,
            ViolationService violationService,
            SessionManager sessionManager,
            ClientProtocolProvider clientProtocolProvider,
            StaffNotifier staffNotifier
    ) {
        this.plugin = plugin;
        this.configurationManager = configurationManager;
        this.violationService = violationService;
        this.sessionManager = sessionManager;
        this.clientProtocolProvider = clientProtocolProvider;
        this.staffNotifier = staffNotifier;
    }

    public double add(Player player, String checkCode, double increase, String details) {
        CheckKey key = new CheckKey(player.getUniqueId(), checkCode);
        CheckState state = levels.computeIfAbsent(key, ignored -> new CheckState());
        long now = System.currentTimeMillis();
        double level;
        boolean alert;
        boolean record;

        synchronized (state) {
            decay(state, now);
            double previous = state.level;
            state.level = Math.min(1000.0, state.level + increase);
            level = state.level;
            alert = previous < configurationManager.get().alertThreshold()
                    && level >= configurationManager.get().alertThreshold();
            record = now - state.lastRecordedAt >= 5000L || alert;
            if (record) {
                state.lastRecordedAt = now;
            }
        }

        if (record) {
            ViolationRecord violation = new ViolationRecord(
                    sessionManager.getSessionId(player),
                    player.getUniqueId(),
                    player.getName(),
                    checkCode,
                    now,
                    level,
                    details,
                    clientProtocolProvider.getProtocol(player.getUniqueId()),
                    Math.max(0, player.getPing())
            );
            violationService.record(violation);
            plugin.getServer().getPluginManager().callEvent(new ZenixEvents.ViolationDetected(violation, alert));
            staffNotifier.notifyViolation(violation, alert);
        }
        return level;
    }

    public void decay(Player player, String checkCode) {
        CheckState state = levels.get(new CheckKey(player.getUniqueId(), checkCode));
        if (state != null) {
            synchronized (state) {
                decay(state, System.currentTimeMillis());
            }
        }
    }

    public void clear(UUID playerId) {
        levels.keySet().removeIf(key -> key.playerId.equals(playerId));
    }

    public long getRecordedCount() {
        return violationService.getAcceptedEvents();
    }

    public long getDroppedCount() {
        return violationService.getDroppedEvents();
    }

    public int getQueueSize() {
        return violationService.getQueueSize();
    }

    private void decay(CheckState state, long now) {
        long elapsedMillis = Math.max(0L, now - state.lastUpdatedAt);
        double decayPerMinute = configurationManager.get().violationDecayPerMinute();
        state.level = Math.max(0.0, state.level - decayPerMinute * elapsedMillis / 60_000.0);
        state.lastUpdatedAt = now;
    }

    private record CheckKey(UUID playerId, String checkCode) {
    }

    private static final class CheckState {
        private double level;
        private long lastUpdatedAt = System.currentTimeMillis();
        private long lastRecordedAt;
    }
}
