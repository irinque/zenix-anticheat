package irinque.zenixac;

import irinque.zenixac.model.PlayerSession;
import irinque.zenixac.model.ViolationRecord;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class ZenixEvents {
    private ZenixEvents() {
    }

    public static final class ViolationDetected extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final ViolationRecord violation;
        private final boolean staffAlert;

        public ViolationDetected(ViolationRecord violation, boolean staffAlert) {
            this.violation = Objects.requireNonNull(violation, "violation");
            this.staffAlert = staffAlert;
        }

        public ViolationRecord getViolation() {
            return violation;
        }

        public boolean shouldAlertStaff() {
            return staffAlert;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    public static final class PlayerSessionStart extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final PlayerSession session;

        public PlayerSessionStart(PlayerSession session) {
            this.session = Objects.requireNonNull(session, "session");
        }

        public PlayerSession getSession() {
            return session;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    public static final class PlayerSessionEnd extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final PlayerSession session;
        private final long endedAt;

        public PlayerSessionEnd(PlayerSession session, long endedAt) {
            this.session = Objects.requireNonNull(session, "session");
            this.endedAt = endedAt;
        }

        public PlayerSession getSession() {
            return session;
        }

        public long getEndedAt() {
            return endedAt;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    public static final class ConfigurationReload extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final boolean successful;
        private final String reason;

        public ConfigurationReload(boolean successful, String reason) {
            this.successful = successful;
            this.reason = reason == null ? "" : reason;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    public static final class CommandCompleted extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final String subcommand;
        private final UUID playerId;

        public CommandCompleted(String subcommand, UUID playerId) {
            this.subcommand = subcommand;
            this.playerId = playerId;
        }

        public String getSubcommand() {
            return subcommand;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }
}
