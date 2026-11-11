package irinque.zenixac.storage;

import irinque.zenixac.model.PlayerProfile;
import irinque.zenixac.model.PlayerSession;
import irinque.zenixac.model.ViolationRecord;
import java.util.List;
import java.util.UUID;

public interface ViolationStore extends AutoCloseable {
    void initialize(int retentionDays) throws Exception;

    void startSession(PlayerSession session) throws Exception;

    void endSession(UUID sessionId, long endedAt) throws Exception;

    void saveViolation(ViolationRecord record) throws Exception;

    List<ViolationRecord> getRecent(UUID playerId, int limit) throws Exception;

    List<ViolationRecord> getRecent(UUID playerId, UUID sessionId, int limit) throws Exception;

    List<ViolationRecord> getRecent(String playerName, UUID sessionId, int limit) throws Exception;

    PlayerProfile getProfile(UUID playerId) throws Exception;

    PlayerProfile getProfile(String playerName) throws Exception;

    @Override
    void close() throws Exception;
}
