package irinque.zenixac.checks;

import irinque.zenixac.config.ConfigurationManager;
import irinque.zenixac.config.PluginSettings.ActionRateSettings;
import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;

public final class ActionRateCheck implements Listener {
    private static final String SWING_CHECK_CODE = "ACTION_RATE_SWING";
    private static final String INTERACT_CHECK_CODE = "ACTION_RATE_INTERACT";

    private final ActionRateWindow rateWindow = new ActionRateWindow();
    private final ConfigurationManager configurationManager;
    private final ViolationManager violationManager;

    public ActionRateCheck(ConfigurationManager configurationManager, ViolationManager violationManager) {
        this.configurationManager = configurationManager;
        this.violationManager = violationManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmSwing(PlayerAnimationEvent event) {
        ActionRateSettings settings = configurationManager.get().actionRate();
        if (settings.enabled()) {
            record(event.getPlayer(), "swing", SWING_CHECK_CODE, settings.maxSwingsPerSecond(), settings);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ActionRateSettings settings = configurationManager.get().actionRate();
        if (!settings.enabled() || event.getAction() == Action.PHYSICAL) {
            return;
        }
        record(event.getPlayer(), "interact", INTERACT_CHECK_CODE,
                settings.maxInteractionsPerSecond(), settings);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        rateWindow.clear(event.getPlayer().getUniqueId());
    }

    private void record(Player player, String action, String checkCode, int limit, ActionRateSettings settings) {
        ActionRateWindow.Result result = rateWindow.record(
                player.getUniqueId(), action, System.nanoTime(), limit
        );
        if (result.exceeded()) {
            String details = String.format(Locale.ROOT,
                    "action=%s; count=%d; limit=%d per second",
                    action, result.count(), limit);
            violationManager.add(player, checkCode, settings.violationIncrease(), details);
        }
    }
}
