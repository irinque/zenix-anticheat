package irinque.zenixac.checks;

import irinque.zenixac.config.ConfigurationManager;
import irinque.zenixac.config.PluginSettings.PacketOrderSettings;
import irinque.zenixac.platform.PlatformScheduler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PacketOrderCheck implements Listener {
    private static final String CHECK_CODE = "PACKET_ORDER_DIG";
    private static final long RECORD_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(5L);

    private final Map<UUID, DigState> states = new ConcurrentHashMap<>();
    private final ConfigurationManager configurationManager;
    private final ViolationManager violationManager;
    private final PlatformScheduler scheduler;

    public PacketOrderCheck(
            ConfigurationManager configurationManager,
            ViolationManager violationManager,
            PlatformScheduler scheduler
    ) {
        this.configurationManager = configurationManager;
        this.violationManager = violationManager;
        this.scheduler = scheduler;
    }

    public void onDiggingPacket(Player player, UUID playerId, String action, BlockPosition position) {
        PacketOrderSettings settings = configurationManager.get().packetOrder();
        if (!settings.enabled()) {
            return;
        }

        long now = System.nanoTime();
        DigState state = states.computeIfAbsent(playerId, ignored -> new DigState());
        boolean mismatch = false;
        int mismatchCount = 0;
        synchronized (state) {
            if (action.equals("START_DIGGING")) {
                state.activeBlock = position;
                return;
            }
            if (action.equals("CHANGE_DESTROY_DIRECTION")) {
                if (state.activeBlock != null) {
                    state.activeBlock = position;
                }
                return;
            }
            if (!action.equals("FINISHED_DIGGING") && !action.equals("CANCELLED_DIGGING")) {
                return;
            }

            if (state.activeBlock != null && state.activeBlock.equals(position)) {
                state.activeBlock = null;
                return;
            }

            long mismatchWindowNanos = TimeUnit.SECONDS.toNanos(settings.mismatchWindowSeconds());
            if (state.windowStartedAt == 0L || now - state.windowStartedAt > mismatchWindowNanos) {
                state.windowStartedAt = now;
                state.mismatchCount = 0;
            }
            state.mismatchCount++;
            mismatchCount = state.mismatchCount;
            if (mismatchCount >= settings.mismatchLimit()
                    && now - state.lastReportedAt >= RECORD_COOLDOWN_NANOS) {
                state.lastReportedAt = now;
                state.mismatchCount = 0;
                state.activeBlock = null;
                mismatch = true;
            }
        }

        if (mismatch) {
            String details = String.format(Locale.ROOT,
                    "action=%s; unmatched-sequence=%d in %ds",
                    action, mismatchCount, settings.mismatchWindowSeconds());
            scheduler.executeForPlayer(player, () -> {
                if (configurationManager.get().packetOrder().enabled()) {
                    violationManager.add(player, CHECK_CODE, settings.violationIncrease(), details);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    public record BlockPosition(int x, int y, int z) {
    }

    private static final class DigState {
        private BlockPosition activeBlock;
        private long windowStartedAt;
        private long lastReportedAt;
        private int mismatchCount;
    }
}
