package irinque.zenixac.checks;

import irinque.zenixac.config.ConfigurationManager;
import irinque.zenixac.config.PluginSettings.CombatSettings;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;

public final class CombatCheck implements Listener {
    private static final String CHECK_CODE = "COMBAT_REACH";
    private final ConfigurationManager configurationManager;
    private final ViolationManager violationManager;

    public CombatCheck(ConfigurationManager configurationManager, ViolationManager violationManager) {
        this.configurationManager = configurationManager;
        this.violationManager = violationManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        Entity target = event.getEntity();
        CombatSettings settings = configurationManager.get().combat();
        if (!settings.enabled() || isExempt(attacker)
                || (target instanceof Player targetPlayer && isExempt(targetPlayer))) {
            return;
        }

        Location eye = attacker.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        double dx = distanceToAxis(eye.getX(), box.getMinX(), box.getMaxX());
        double dy = distanceToAxis(eye.getY(), box.getMinY(), box.getMaxY());
        double dz = distanceToAxis(eye.getZ(), box.getMinZ(), box.getMaxZ());
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int ping = Math.max(0, attacker.getPing());
        double pingAllowance = Math.min(settings.maxPingCompensation(), ping
                * settings.pingCompensation() / 1000.0);
        double effectiveReach = settings.maxReach() + pingAllowance;
        if (distance > effectiveReach) {
            String details = String.format(Locale.ROOT,
                    "distance=%.2f; limit=%.2f; base=%.2f; ping=%dms",
                    distance, effectiveReach, settings.maxReach(), ping);
            violationManager.add(attacker, CHECK_CODE, settings.violationIncrease(), details);
        }
    }

    private boolean isExempt(Player player) {
        GameMode gameMode = player.getGameMode();
        return player.isInsideVehicle() || player.isFlying() || player.getAllowFlight() || player.isGliding()
                || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;
    }

    private double distanceToAxis(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return Math.max(0.0, value - maximum);
    }
}
