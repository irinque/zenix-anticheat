package irinque.zenixac.checks;

import irinque.zenixac.config.ConfigurationManager;
import irinque.zenixac.config.PluginSettings.VehicleSettings;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public final class VehicleCheck implements Listener {
    private static final String CHECK_CODE = "VEHICLE_SPEED";
    private static final long MIN_SAMPLE_NANOS = TimeUnit.MILLISECONDS.toNanos(40L);
    private static final long MAX_SAMPLE_GAP_NANOS = TimeUnit.MILLISECONDS.toNanos(1500L);
    private static final long REPORT_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(1L);

    private final Map<UUID, Sample> samples = new ConcurrentHashMap<>();
    private final ConfigurationManager configurationManager;
    private final ViolationManager violationManager;

    public VehicleCheck(ConfigurationManager configurationManager, ViolationManager violationManager) {
        this.configurationManager = configurationManager;
        this.violationManager = violationManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        VehicleSettings settings = configurationManager.get().vehicle();
        if (!settings.enabled()) {
            samples.remove(event.getVehicle().getUniqueId());
            return;
        }

        Vehicle vehicle = event.getVehicle();
        Player rider = firstPlayer(vehicle);
        if (rider == null) {
            samples.remove(vehicle.getUniqueId());
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().getUID().equals(to.getWorld().getUID())) {
            reset(vehicle.getUniqueId(), rider.getUniqueId(), to, System.nanoTime());
            return;
        }

        long now = System.nanoTime();
        Sample sample = samples.computeIfAbsent(vehicle.getUniqueId(), ignored -> new Sample(rider.getUniqueId(), to, now));
        synchronized (sample) {
            if (!sample.riderId.equals(rider.getUniqueId()) || !sample.worldId.equals(to.getWorld().getUID())) {
                sample.reset(rider.getUniqueId(), to, now);
                return;
            }

            long elapsed = now - sample.sampledAt;
            if (elapsed < MIN_SAMPLE_NANOS) {
                return;
            }
            if (elapsed > MAX_SAMPLE_GAP_NANOS) {
                sample.reset(rider.getUniqueId(), to, now);
                return;
            }

            double dx = to.getX() - sample.x;
            double dz = to.getZ() - sample.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double speed = distance / (elapsed / 1_000_000_000.0);
            sample.resetPosition(to, now);

            if (speed > settings.maxHorizontalSpeed()) {
                sample.consecutiveFastSamples++;
                if (sample.consecutiveFastSamples >= settings.consecutiveSamples()
                        && now - sample.lastReportedAt >= REPORT_COOLDOWN_NANOS) {
                    sample.lastReportedAt = now;
                    sample.consecutiveFastSamples = 0;
                    String details = String.format(Locale.ROOT,
                            "speed=%.2f blocks/s; limit=%.2f; sample=%dms",
                            speed, settings.maxHorizontalSpeed(), TimeUnit.NANOSECONDS.toMillis(elapsed));
                    violationManager.add(rider, CHECK_CODE, settings.violationIncrease(), details);
                }
            } else {
                sample.consecutiveFastSamples = 0;
                violationManager.decay(rider, CHECK_CODE);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent event) {
        samples.remove(event.getVehicle().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        samples.remove(event.getVehicle().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        samples.entrySet().removeIf(entry -> entry.getValue().riderId.equals(playerId));
    }

    private Player firstPlayer(Vehicle vehicle) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void reset(UUID vehicleId, UUID riderId, Location location, long now) {
        if (location == null || location.getWorld() == null) {
            samples.remove(vehicleId);
            return;
        }
        samples.compute(vehicleId, (ignored, sample) -> {
            if (sample == null) {
                return new Sample(riderId, location, now);
            }
            synchronized (sample) {
                sample.reset(riderId, location, now);
                return sample;
            }
        });
    }

    private static final class Sample {
        private UUID riderId;
        private UUID worldId;
        private double x;
        private double z;
        private long sampledAt;
        private long lastReportedAt;
        private int consecutiveFastSamples;

        private Sample(UUID riderId, Location location, long sampledAt) {
            reset(riderId, location, sampledAt);
        }

        private void reset(UUID nextRiderId, Location location, long now) {
            riderId = nextRiderId;
            worldId = location.getWorld().getUID();
            resetPosition(location, now);
            lastReportedAt = 0L;
            consecutiveFastSamples = 0;
        }

        private void resetPosition(Location location, long now) {
            x = location.getX();
            z = location.getZ();
            sampledAt = now;
        }
    }
}
