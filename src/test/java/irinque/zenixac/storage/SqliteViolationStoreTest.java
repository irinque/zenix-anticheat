package irinque.zenixac.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import irinque.zenixac.model.PlayerProfile;
import irinque.zenixac.model.PlayerSession;
import irinque.zenixac.model.ViolationRecord;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteViolationStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void savesSessionsAndFindsEventsByPlayerAndSession() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        try (ViolationStore store = new SqliteViolationStore(tempDirectory.resolve("history.db").toFile())) {
            store.initialize(30);
            store.startSession(new PlayerSession(sessionId, playerId, "Player", 1000));
            store.saveViolation(new ViolationRecord(
                    sessionId, playerId, "Player", "MOVE_SAMPLE", 1500, 2.5,
                    "speed 19.1", null, 45
            ));

            List<ViolationRecord> events = store.getRecent(playerId, sessionId, 10);
            List<ViolationRecord> eventsByName = store.getRecent("player", sessionId, 10);
            PlayerProfile profile = store.getProfile(playerId);
            PlayerProfile profileByName = store.getProfile("PLAYER");

            assertEquals(1, events.size());
            assertEquals(1, eventsByName.size());
            assertEquals("MOVE_SAMPLE", events.get(0).checkCode());
            assertNull(events.get(0).clientProtocol());
            assertEquals(1, profile.eventCount());
            assertEquals("Player", profile.playerName());
            assertEquals(2.5, profile.highestRecordedViolationLevel());
            assertEquals(1, profileByName.eventCount());
        }
    }
}
