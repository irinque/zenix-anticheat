package irinque.zenixac.checks;

import irinque.zenixac.config.ConfigurationManager;
import irinque.zenixac.config.PluginSettings.FlightSettings;
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

public final class FlightCheck implements Listener {
    private static final String ASCENT_CHECK_CODE = "FLIGHT_ASCENT";
    private static final String HOVER_CHECK_CODE = "FLIGHT_HOVER";
    private static final long MIN_SAMPLE_NANOS = TimeUnit.MILLISECONDS.toNanos(40L);
    private static final long MAX_SAMPLE_GAP_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);
    private static final long VELOCITY_GRACE_NANOS = TimeUnit.MILLISECONDS.toNanos(1500L);
    private static final long HOVER_LIMIT_NANOS = TimeUnit.MILLISECONDS.toNanos(2500L);
    private static final long HOVER_REPORT_COOLDOWN_NANOS = TimeUnit.MILLISECONDS.toNanos(2500L);

    private final Map<UUID, Sample> samples = new ConcurrentHashMap<>();
    private final ConfigurationManager configurationManager;
    private final ViolationManager violationManager;

    public FlightCheck(ConfigurationManager configurationManager, ViolationManager violationManager) {
        this.configurationManager = configurationManager;
        this.violationManager = violationManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        UUID playerId = player.getUniqueId();
        if (to == null || to.getWorld() == null || from.getWorld() == null
                || !from.getWorld().getUID().equals(to.getWorld().getUID())) {
            reset(playerId, to, System.nanoTime());
            return;
        }

        FlightSettings settings = configurationManager.get().flight();
        if (!settings.enabled() || event instanceof PlayerTeleportEvent || isExempt(player, to)
                || player.isOnGround()) {
            reset(playerId, to, System.nanoTime());
            return;
        }

        long now = System.nanoTime();
        Sample sample = samples.computeIfAbsent(playerId, ignored -> new Sample(to, now));
        synchronized (sample) {
            if (!sample.worldId.equals(to.getWorld().getUID())) {
                sample.reset(to, now);
                return;
            }
            if (now < sample.ignoreUntil) {
                sample.resetPosition(to, now);
                return;
            }

            long elapsed = now - sample.sampledAt;
            if (elapsed < MIN_SAMPLE_NANOS) {
                return;
            }
            if (elapsed > MAX_SAMPLE_GAP_NANOS) {
                sample.reset(to, now);
                return;
            }

            double deltaY = to.getY() - sample.y;
            double deltaX = to.getX() - sample.x;
            double deltaZ = to.getZ() - sample.z;
            sample.resetPosition(to, now);
            if (deltaY > 0.0 && deltaY / (elapsed / 1_000_000_000.0) > settings.maxVerticalSpeed()) {
                sample.fastAscentSamples++;
                if (sample.fastAscentSamples >= settings.consecutiveSamples()
                        && now - sample.lastAscentReportedAt >= TimeUnit.SECONDS.toNanos(1L)) {
                    sample.fastAscentSamples = 0;
                    sample.lastAscentReportedAt = now;
                    String details = String.format(Locale.ROOT,
                            "vertical-speed=%.2f blocks/s; limit=%.2f; sample=%dms",
                            deltaY / (elapsed / 1_000_000_000.0), settings.maxVerticalSpeed(),
                            TimeUnit.NANOSECONDS.toMillis(elapsed));
                    violationManager.add(player, ASCENT_CHECK_CODE, settings.violationIncrease(), details);
                }
            } else {
                sample.fastAscentSamples = 0;
                violationManager.decay(player, ASCENT_CHECK_CODE);
            }

            if (Math.abs(deltaY) <= 0.01 && Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) <= 1.0) {
                if (sample.hoverStartedAt == 0L) {
                    sample.hoverStartedAt = now;
                } else if (now - sample.hoverStartedAt >= HOVER_LIMIT_NANOS
                        && now - sample.lastHoverReportedAt >= HOVER_REPORT_COOLDOWN_NANOS) {
                    sample.lastHoverReportedAt = now;
                    sample.hoverStartedAt = now;
                    violationManager.add(player, HOVER_CHECK_CODE, settings.violationIncrease(),
                            "vertical-motion=0.00 blocks; airborne=2500ms");
                }
            } else {
                sample.hoverStartedAt = 0L;
                violationManager.decay(player, HOVER_CHECK_CODE);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        markRecentImpulse(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            markRecentImpulse(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        reset(event.getPlayer().getUniqueId(), event.getTo(), System.nanoTime());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        reset(event.getPlayer().getUniqueId(), event.getPlayer().getLocation(), System.nanoTime());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }

    private void markRecentImpulse(Player player) {
        if (!configurationManager.get().flight().enabled()) {
            return;
        }
        long now = System.nanoTime();
        Sample sample = samples.computeIfAbsent(
                player.getUniqueId(), ignored -> new Sample(player.getLocation(), now)
        );
        synchronized (sample) {
            sample.ignoreUntil = now + VELOCITY_GRACE_NANOS;
            sample.fastAscentSamples = 0;
            sample.hoverStartedAt = 0L;
        }
    }

    private boolean isExempt(Player player, Location to) {
        GameMode gameMode = player.getGameMode();
        if (player.isInsideVehicle() || player.isFlying() || player.getAllowFlight()
                || player.isGliding() || player.isSwimming() || player.isRiptiding()
                || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR
                || to.getBlock().isLiquid()) {
            return true;
        }

        Material current = to.getBlock().getType();
        Material below = to.clone().subtract(0.0, 0.1, 0.0).getBlock().getType();
        if (isSpecialMovementBlock(current) || isSpecialMovementBlock(below)) {
            return true;
        }
        return player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || player.hasPotionEffect(PotionEffectType.JUMP);
    }

    private boolean isSpecialMovementBlock(Material material) {
        return material == Material.LADDER || material == Material.VINE
                || material == Material.WEEPING_VINES || material == Material.TWISTING_VINES
                || material == Material.SCAFFOLDING || material == Material.COBWEB
                || material == Material.POWDER_SNOW || material == Material.SLIME_BLOCK
                || material == Material.HONEY_BLOCK || material == Material.BUBBLE_COLUMN;
    }

    private void reset(UUID playerId, Location location, long now) {
        if (location == null || location.getWorld() == null) {
            samples.remove(playerId);
            return;
        }
        samples.compute(playerId, (ignored, sample) -> {
            if (sample == null) {
                return new Sample(location, now);
            }
            synchronized (sample) {
                sample.reset(location, now);
                return sample;
            }
        });
    }

    private static final class Sample {
        private UUID worldId;
        private double x;
        private double y;
        private double z;
        private long sampledAt;
        private long ignoreUntil;
        private long hoverStartedAt;
        private long lastAscentReportedAt;
        private long lastHoverReportedAt;
        private int fastAscentSamples;

        private Sample(Location location, long sampledAt) {
            reset(location, sampledAt);
        }

        private void reset(Location location, long now) {
            worldId = location.getWorld().getUID();
            resetPosition(location, now);
            ignoreUntil = 0L;
            hoverStartedAt = 0L;
            fastAscentSamples = 0;
        }

        private void resetPosition(Location location, long now) {
            x = location.getX();
            y = location.getY();
            z = location.getZ();
            sampledAt = now;
        }
    }
}
