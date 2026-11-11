package irinque.zenixac.commands;

import irinque.zenixac.ZenixAnticheat;
import irinque.zenixac.ZenixEvents;
import irinque.zenixac.config.ConfigurationManager;
import irinque.zenixac.model.PlayerProfile;
import irinque.zenixac.model.ViolationRecord;
import irinque.zenixac.notification.StaffNotifier;
import irinque.zenixac.platform.PacketEventsBridge;
import irinque.zenixac.platform.PlatformScheduler;
import irinque.zenixac.storage.ViolationService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ZenixCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final List<String> SUBCOMMANDS = List.of(
            "help", "alerts", "debug", "profile", "history", "reload", "performance"
    );

    private final ZenixAnticheat plugin;
    private final ConfigurationManager configurationManager;
    private final ViolationService violationService;
    private final StaffNotifier staffNotifier;
    private final PlatformScheduler scheduler;
    private final PacketEventsBridge packetEventsBridge;

    public ZenixCommand(
            ZenixAnticheat plugin,
            ConfigurationManager configurationManager,
            ViolationService violationService,
            StaffNotifier staffNotifier,
            PlatformScheduler scheduler,
            PacketEventsBridge packetEventsBridge
    ) {
        this.plugin = plugin;
        this.configurationManager = configurationManager;
        this.violationService = violationService;
        this.staffNotifier = staffNotifier;
        this.scheduler = scheduler;
        this.packetEventsBridge = packetEventsBridge;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            send(sender, render("help", Map.of()));
            fireCommandEvent(sender, args.length == 0 ? "help" : args[0]);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "alerts" -> toggleAlerts(sender, args);
            case "debug" -> toggleDebug(sender, args);
            case "profile" -> profile(sender, args);
            case "history" -> history(sender, args);
            case "reload" -> reload(sender, args);
            case "performance" -> performance(sender, args);
            default -> send(sender, render("unknown-command", Map.of()));
        }
        fireCommandEvent(sender, args[0].toLowerCase(Locale.ROOT));
        return true;
    }

    private void toggleAlerts(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "zenixac.alerts")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            send(sender, render("invalid-usage", Map.of("usage", "/zenixac alerts")));
            return;
        }
        if (args.length != 1) {
            send(sender, render("invalid-usage", Map.of("usage", "/zenixac alerts")));
            return;
        }
        send(sender, render(staffNotifier.toggleAlerts(player) ? "alerts-enabled" : "alerts-disabled", Map.of()));
    }

    private void toggleDebug(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "zenixac.debug")) {
            return;
        }
        if (!(sender instanceof Player player) || args.length != 1) {
            send(sender, render("invalid-usage", Map.of("usage", "/zenixac debug")));
            return;
        }
        if (!configurationManager.get().debugNotificationsEnabled()) {
            send(sender, render("debug-config-disabled", Map.of()));
            return;
        }
        send(sender, render(staffNotifier.toggleDebug(player) ? "debug-enabled" : "debug-disabled", Map.of()));
    }

    private void profile(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "zenixac.profile")) {
            return;
        }
        if (args.length != 2 || !isValidPlayerName(args[1])) {
            send(sender, render("invalid-usage", Map.of("usage", "/zenixac profile <player>")));
            return;
        }
        String playerName = args[1];
        violationService.profile(playerName).whenComplete((profile, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "[ZenixAC] Could not load profile for " + playerName, error);
                sendAsync(sender, render("history-unavailable", Map.of()));
            } else if (profile.playerName() == null) {
                sendAsync(sender, render("profile-empty", Map.of("player", playerName)));
            } else {
                sendAsync(sender, render("profile-summary", Map.of(
                        "player", profile.playerName(),
                        "events", Long.toString(profile.eventCount()),
                        "level", String.format(Locale.ROOT, "%.2f", profile.highestRecordedViolationLevel())
                )));
            }
        });
    }

    private void history(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "zenixac.history")) {
            return;
        }
        if ((args.length != 2 && args.length != 3) || !isValidPlayerName(args[1])) {
            send(sender, render("invalid-usage", Map.of("usage", "/zenixac history <player> [session-id]")));
            return;
        }

        UUID sessionId = null;
        if (args.length == 3) {
            try {
                sessionId = UUID.fromString(args[2]);
            } catch (IllegalArgumentException exception) {
                send(sender, render("invalid-usage", Map.of("usage", "/zenixac history <player> [session-id]")));
                return;
            }
        }

        String playerName = args[1];
        violationService.recent(playerName, sessionId, 10).whenComplete((records, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "[ZenixAC] Could not load history for " + playerName, error);
                sendAsync(sender, render("history-unavailable", Map.of()));
                return;
            }
            if (records.isEmpty()) {
                sendAsync(sender, render("history-empty", Map.of("player", playerName)));
                return;
            }
            for (ViolationRecord record : records) {
                sendAsync(sender, render("history-entry", Map.of(
                        "time", HISTORY_TIME.format(Instant.ofEpochMilli(record.occurredAt())),
                        "check", record.checkCode(),
                        "level", String.format(Locale.ROOT, "%.2f", record.violationLevel()),
                        "details", record.details(),
                        "session", record.sessionId().toString(),
                        "protocol", record.clientProtocol() == null ? "unknown" : record.clientProtocol().toString(),
                        "ping", Integer.toString(record.ping())
                )));
            }
        });
    }

    private void reload(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "zenixac.reload")) {
            return;
        }
        if (args.length != 1) {
            send(sender, render("invalid-usage", Map.of("usage", "/zenixac reload")));
            return;
        }
        try {
            configurationManager.reload();
            boolean packetOrderEnabled = configurationManager.get().packetOrder().enabled();
            boolean packetOrderAvailable = packetEventsBridge.refresh(packetOrderEnabled);
            if (packetOrderEnabled && !packetOrderAvailable) {
                plugin.getLogger().warning("[ZenixAC] Packet-order check is unavailable; install PacketEvents "
                        + "2.14.0 or newer and restart");
            }
            staffNotifier.refreshRecipients();
            plugin.getServer().getPluginManager().callEvent(new ZenixEvents.ConfigurationReload(true, ""));
            send(sender, render("reload-success", Map.of()));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "[ZenixAC] Configuration reload failed; active settings were preserved", exception);
            plugin.getServer().getPluginManager().callEvent(
                    new ZenixEvents.ConfigurationReload(false, exception.getMessage())
            );
            send(sender, render("reload-failed", Map.of()));
        }
    }

    private void performance(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "zenixac.performance")) {
            return;
        }
        if (args.length != 1) {
            send(sender, render("invalid-usage", Map.of("usage", "/zenixac performance")));
            return;
        }
        send(sender, render("performance", Map.of(
                "processed", Long.toString(violationService.getAcceptedEvents()),
                "dropped", Long.toString(violationService.getDroppedEvents()),
                "queued", Integer.toString(violationService.getQueueSize())
        )));
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        send(sender, render("no-permission", Map.of()));
        return false;
    }

    private String render(String key, Map<String, String> replacements) {
        String prefix = configurationManager.message("prefix");
        String content = configurationManager.message(key);
        if (content == null) {
            content = "&cMissing message: " + key;
        }
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            content = content.replace("{" + replacement.getKey() + "}", replacement.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', prefix + content);
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    private void sendAsync(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            scheduler.executeForPlayer(player, () -> player.sendMessage(message));
        } else {
            plugin.getLogger().info(ChatColor.stripColor(message));
        }
    }

    private void fireCommandEvent(CommandSender sender, String subcommand) {
        UUID playerId = sender instanceof Player player ? player.getUniqueId() : null;
        plugin.getServer().getPluginManager().callEvent(new ZenixEvents.CommandCompleted(subcommand, playerId));
    }

    private boolean isValidPlayerName(String playerName) {
        return playerName != null && playerName.matches("[A-Za-z0-9_]{1,16}");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String subcommand : SUBCOMMANDS) {
            if (subcommand.startsWith(prefix)) {
                matches.add(subcommand);
            }
        }
        return matches;
    }
}
