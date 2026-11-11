package irinque.zenixac.model;

import java.util.Objects;
import java.util.UUID;

public record PlayerSession(UUID sessionId, UUID playerId, String playerName, long startedAt) {
    public PlayerSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        playerName = playerName == null ? "" : playerName.substring(0, Math.min(playerName.length(), 32));
        if (startedAt < 0) {
            throw new IllegalArgumentException("Session start time cannot be negative");
        }
    }
}
