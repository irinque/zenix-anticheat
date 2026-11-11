package irinque.zenixac.notification;

import irinque.zenixac.config.ConfigurationManager;
import irinque.zenixac.model.ViolationRecord;
import irinque.zenixac.platform.PlatformScheduler;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class StaffNotifier {
    private static final int MAX_NOTIFICATIONS_PER_SECOND = 8;

    private final Map<UUID, Player> onlinePlayers = new ConcurrentHashMap<>();
    private final Set<UUID> staffRecipients = ConcurrentHashMap.newKeySet();
    private final Set<UUID> alertsDisabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> debugEnabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, DeliveryWindow> deliveryWindows = new ConcurrentHashMap<>();
    private final ConfigurationManager configurationManager;
    private final PlatformScheduler scheduler;

    public StaffNotifier(ConfigurationManager configurationManager, PlatformScheduler scheduler) {
        this.configurationManager = configurationManager;
        this.scheduler = scheduler;
    }

    public void track(Player player) {
        onlinePlayers.put(player.getUniqueId(), player);
        if (player.hasPermission(configurationManager.get().alertPermission())
                || player.hasPermission(configurationManager.get().debugPermission())) {
            staffRecipients.add(player.getUniqueId());
        } else {
            staffRecipients.remove(player.getUniqueId());
        }
    }

    public void untrack(Player player) {
        UUID playerId = player.getUniqueId();
        onlinePlayers.remove(playerId);
        staffRecipients.remove(playerId);
        alertsDisabled.remove(playerId);
        debugEnabled.remove(playerId);
        deliveryWindows.remove(playerId);
    }

    public boolean toggleAlerts(Player player) {
        UUID playerId = player.getUniqueId();
        staffRecipients.add(playerId);
        if (!alertsDisabled.add(playerId)) {
            alertsDisabled.remove(playerId);
            return true;
        }
        return false;
    }

    public boolean toggleDebug(Player player) {
        UUID playerId = player.getUniqueId();
        staffRecipients.add(playerId);
        if (!debugEnabled.add(playerId)) {
            debugEnabled.remove(playerId);
            return false;
        }
        return true;
    }

    public void notifyViolation(ViolationRecord record, boolean alert) {
        var settings = configurationManager.get();
        if (!settings.notificationsEnabled()) {
            return;
        }

        for (UUID recipientId : staffRecipients) {
            Player recipient = onlinePlayers.get(recipientId);
            if (recipient == null) {
                continue;
            }
            boolean debug = debugEnabled.contains(recipientId) && settings.debugNotificationsEnabled();
            if (!alert && !debug) {
                continue;
            }
            if (!allowDelivery(recipientId)) {
                continue;
            }

            scheduler.executeForPlayer(recipient, () -> {
                if (alert && !alertsDisabled.contains(recipientId) && recipient.hasPermission(settings.alertPermission())) {
                    recipient.sendMessage(format(settings.alertFormat(), record));
                }
                if (debug && recipient.hasPermission(settings.debugPermission())) {
                    recipient.sendMessage(format(settings.debugFormat(), record));
                }
            });
        }
    }

    public void refreshRecipients() {
        for (Map.Entry<UUID, Player> entry : onlinePlayers.entrySet()) {
            scheduler.executeForPlayer(entry.getValue(), () -> {
                if (entry.getValue().hasPermission(configurationManager.get().alertPermission())
                        || entry.getValue().hasPermission(configurationManager.get().debugPermission())) {
                    staffRecipients.add(entry.getKey());
                } else {
                    staffRecipients.remove(entry.getKey());
                }
            });
        }
    }

    private boolean allowDelivery(UUID playerId) {
        long now = System.nanoTime();
        DeliveryWindow window = deliveryWindows.computeIfAbsent(playerId, ignored -> new DeliveryWindow());
        synchronized (window) {
            if (now - window.startedAt >= TimeUnit.SECONDS.toNanos(1)) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= MAX_NOTIFICATIONS_PER_SECOND) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private String format(String template, ViolationRecord record) {
        String message = template
                .replace("{player}", record.playerName())
                .replace("{check}", record.checkCode())
                .replace("{level}", String.format(java.util.Locale.ROOT, "%.2f", record.violationLevel()))
                .replace("{details}", record.details());
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private static final class DeliveryWindow {
        private long startedAt = System.nanoTime();
        private int count;
    }
}
