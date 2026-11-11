package irinque.zenixac.checks;

import irinque.zenixac.config.ConfigurationManager;
import irinque.zenixac.config.PluginSettings.BlockInteractionSettings;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class BlockInteractionCheck implements Listener {
    private static final String CHECK_CODE = "BLOCK_ACTION_RATE";

    private final ActionRateWindow rateWindow = new ActionRateWindow();
    private final ConfigurationManager configurationManager;
    private final ViolationManager violationManager;

    public BlockInteractionCheck(ConfigurationManager configurationManager, ViolationManager violationManager) {
        this.configurationManager = configurationManager;
        this.violationManager = violationManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        record(event.getPlayer(), "break");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        record(event.getPlayer(), "place");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        rateWindow.clear(event.getPlayer().getUniqueId());
    }

    private void record(Player player, String action) {
        BlockInteractionSettings settings = configurationManager.get().blockInteraction();
        if (!settings.enabled() || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        ActionRateWindow.Result result = rateWindow.record(
                player.getUniqueId(), "block-actions", System.nanoTime(), settings.maxActionsPerSecond()
        );
        if (result.exceeded()) {
            String details = String.format(Locale.ROOT,
                    "action=%s; count=%d; limit=%d per second",
                    action, result.count(), settings.maxActionsPerSecond());
            violationManager.add(player, CHECK_CODE, settings.violationIncrease(), details);
        }
    }
}
