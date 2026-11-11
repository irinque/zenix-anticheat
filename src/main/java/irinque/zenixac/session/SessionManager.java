package irinque.zenixac.session;

import irinque.zenixac.ZenixEvents;
import irinque.zenixac.model.PlayerSession;
import irinque.zenixac.storage.ViolationService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class SessionManager {
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final ViolationService violationService;
    private final Plugin plugin;

    public SessionManager(Plugin plugin, ViolationService violationService) {
        this.plugin = plugin;
        this.violationService = violationService;
    }

    public PlayerSession start(Player player) {
        PlayerSession session = new PlayerSession(
                UUID.randomUUID(),
                player.getUniqueId(),
                player.getName(),
                System.currentTimeMillis()
        );
        sessions.put(player.getUniqueId(), session);
        violationService.startSession(session);
        plugin.getServer().getPluginManager().callEvent(new ZenixEvents.PlayerSessionStart(session));
        return session;
    }

    public void end(Player player) {
        PlayerSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            end(session, System.currentTimeMillis());
        }
    }

    public UUID getSessionId(Player player) {
        PlayerSession session = sessions.get(player.getUniqueId());
        return session == null ? start(player).sessionId() : session.sessionId();
    }

    public void clear() {
        sessions.clear();
    }

    public void close() {
        long endedAt = System.currentTimeMillis();
        for (PlayerSession session : sessions.values()) {
            end(session, endedAt);
        }
        sessions.clear();
    }

    private void end(PlayerSession session, long endedAt) {
        violationService.endSession(session.sessionId(), endedAt);
        plugin.getServer().getPluginManager().callEvent(new ZenixEvents.PlayerSessionEnd(session, endedAt));
    }
}
