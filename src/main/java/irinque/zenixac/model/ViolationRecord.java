package irinque.zenixac.model;

import java.util.Objects;
import java.util.UUID;

public record ViolationRecord(
        UUID sessionId,
        UUID playerId,
        String playerName,
        String checkCode,
        long occurredAt,
        double violationLevel,
        String details,
        Integer clientProtocol,
        int ping
) {
    public ViolationRecord {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        playerName = sanitize(playerName, 32);
        checkCode = sanitize(checkCode, 48);
        details = sanitize(details, 256);
        if (occurredAt < 0 || !Double.isFinite(violationLevel) || violationLevel < 0 || ping < 0) {
            throw new IllegalArgumentException("Invalid violation record values");
        }
        if (clientProtocol != null && clientProtocol < 0) {
            throw new IllegalArgumentException("Client protocol cannot be negative");
        }
    }

    private static String sanitize(String value, int maximumLength) {
        String source = value == null ? "" : value;
        StringBuilder result = new StringBuilder(Math.min(source.length(), maximumLength));
        for (int index = 0; index < source.length() && result.length() < maximumLength; index++) {
            char character = source.charAt(index);
            if (!Character.isISOControl(character)) {
                result.append(character);
            }
        }
        return result.toString();
    }
}
