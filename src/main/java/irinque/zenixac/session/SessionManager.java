package irinque.zenixac.session;

import irinque.zenixac.model.PlayerSession;
import irinque.zenixac.storage.ViolationService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class SessionManager {
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final ViolationService violationService;

    public SessionManager(ViolationService violationService) {
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
        return session;
    }

    public void end(Player player) {
        PlayerSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            violationService.endSession(session.sessionId(), System.currentTimeMillis());
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
            violationService.endSession(session.sessionId(), endedAt);
        }
        sessions.clear();
    }
}
