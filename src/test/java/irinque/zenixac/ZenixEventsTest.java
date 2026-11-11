package irinque.zenixac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import irinque.zenixac.model.ViolationRecord;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ZenixEventsTest {
    @Test
    void exposesViolationRecordAndAlertState() {
        ViolationRecord record = new ViolationRecord(
                UUID.randomUUID(), UUID.randomUUID(), "Player", "MOVE_RATE", 1000,
                3.0, "speed=19", null, 30
        );
        ZenixEvents.ViolationDetected event = new ZenixEvents.ViolationDetected(record, true);

        assertEquals(record, event.getViolation());
        assertTrue(event.shouldAlertStaff());
        assertNotNull(event.getHandlers());
    }

    @Test
    void exposesReloadResultAndHandlerList() {
        ZenixEvents.ConfigurationReload event = new ZenixEvents.ConfigurationReload(false, "checks.yml: limit invalid");

        assertFalse(event.isSuccessful());
        assertEquals("checks.yml: limit invalid", event.getReason());
        assertNotNull(ZenixEvents.ConfigurationReload.getHandlerList());
    }
}
