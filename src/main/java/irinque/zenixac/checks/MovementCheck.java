package irinque.zenixac.checks;

import irinque.zenixac.config.ConfigurationManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffectType;

public final class MovementCheck implements Listener {
    private static final String CHECK_CODE = "MOVE_RATE";
    private static final long LAG_GAP_LIMIT_MILLIS = 3000L;
    private static final long VELOCITY_GRACE_MILLIS = 900L;

    private final Map<UUID, Sample> samples = new ConcurrentHashMap<>();
    private final ConfigurationManager configurationManager;
    private final ViolationManager violationManager;

    public MovementCheck(ConfigurationManager configurationManager, ViolationManager violationManager) {
        this.configurationManager = configurationManager;
        this.violationManager = violationManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || from.getWorld() == null
                || !from.getWorld().getUID().equals(to.getWorld().getUID())) {
            reset(playerId, to);
            return;
        }

        if (event instanceof PlayerTeleportEvent || isExempt(player, to)) {
            reset(playerId, to);
            return;
        }

        long now = monotonicMillis();
        Sample sample = samples.computeIfAbsent(playerId, ignored -> new Sample(from, now));
        if (now < sample.ignoreUntil) {
            sample.reset(to, now);
            return;
        }

        if (sample.worldId == null || !sample.worldId.equals(to.getWorld().getUID())) {
            sample.reset(to, now);
            return;
        }

        long elapsedMillis = now - sample.sampledAt;
        int minimumSampleMillis = configurationManager.get().minimumMovementSampleMillis();
        if (elapsedMillis < minimumSampleMillis) {
            return;
        }
        if (elapsedMillis > LAG_GAP_LIMIT_MILLIS) {
            sample.reset(to, now);
            return;
        }

        double dx = to.getX() - sample.x;
        double dz = to.getZ() - sample.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double speed = horizontalDistance / (elapsedMillis / 1000.0);
        sample.reset(to, now);

        var settings = configurationManager.get();
        if (!settings.movementCheckEnabled() || horizontalDistance < 0.1) {
            violationManager.decay(player, CHECK_CODE);
            return;
        }

        if (speed > settings.maximumHorizontalSpeed()) {
            double ratio = speed / settings.maximumHorizontalSpeed();
            double increase = settings.violationIncrease() * Math.min(2.0, ratio - 1.0);
            String details = String.format(Locale.ROOT,
                    "speed=%.2f blocks/s; sample=%dms; ping=%dms",
                    speed, elapsedMillis, Math.max(0, player.getPing()));
            violationManager.add(player, CHECK_CODE, Math.max(0.1, increase), details);
        } else {
            violationManager.decay(player, CHECK_CODE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Sample sample = samples.get(event.getPlayer().getUniqueId());
        if (sample != null) {
            sample.ignoreUntil = monotonicMillis() + VELOCITY_GRACE_MILLIS;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        reset(event.getPlayer().getUniqueId(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Sample sample = samples.get(player.getUniqueId());
            if (sample != null) {
                sample.ignoreUntil = monotonicMillis() + VELOCITY_GRACE_MILLIS;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
        violationManager.clear(event.getPlayer().getUniqueId());
    }

    public void reset(Player player) {
        reset(player.getUniqueId(), player.getLocation());
    }

    private void reset(UUID playerId, Location location) {
        if (location == null || location.getWorld() == null) {
            samples.remove(playerId);
            return;
        }
        samples.compute(playerId, (ignored, sample) -> {
            if (sample == null) {
                return new Sample(location, monotonicMillis());
            }
            sample.reset(location, monotonicMillis());
            return sample;
        });
    }

    private boolean isExempt(Player player, Location to) {
        if (player.isInsideVehicle() || player.isFlying() || player.getAllowFlight()
                || player.isGliding() || player.isSwimming() || player.isRiptiding()) {
            return true;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        if (to.getBlock().isLiquid()) {
            return true;
        }
        Material ground = to.clone().subtract(0.0, 0.1, 0.0).getBlock().getType();
        if (ground == Material.ICE || ground == Material.PACKED_ICE || ground == Material.BLUE_ICE
                || ground == Material.SLIME_BLOCK || ground == Material.HONEY_BLOCK
                || ground == Material.SOUL_SAND || ground == Material.SOUL_SOIL
                || ground == Material.COBWEB || ground == Material.POWDER_SNOW
                || ground == Material.SCAFFOLDING || ground == Material.LADDER
                || ground == Material.VINE || ground == Material.WEEPING_VINES
                || ground == Material.TWISTING_VINES) {
            return true;
        }
        return player.hasPotionEffect(PotionEffectType.SPEED)
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || player.hasPotionEffect(PotionEffectType.JUMP);
    }

    private long monotonicMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private static final class Sample {
        private UUID worldId;
        private double x;
        private double z;
        private long sampledAt;
        private long ignoreUntil;

        private Sample(Location location, long sampledAt) {
            reset(location, sampledAt);
        }

        private void reset(Location location, long time) {
            worldId = location.getWorld().getUID();
            x = location.getX();
            z = location.getZ();
            sampledAt = time;
        }
    }
}
