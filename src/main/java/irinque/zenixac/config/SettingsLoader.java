package irinque.zenixac.config;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SettingsLoader {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final File dataFolder;

    public SettingsLoader(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public PluginSettings load() throws SettingsException {
        YamlConfiguration general = loadFile("config.yml");
        YamlConfiguration checks = loadFile("checks.yml");
        YamlConfiguration notifications = loadFile("notifications.yml");
        YamlConfiguration storage = loadFile("storage.yml");
        YamlConfiguration punishments = loadFile("punishments.yml");
        String language = string(general, "language").toLowerCase(java.util.Locale.ROOT);
        if (!language.equals("en") && !language.equals("ru")) {
            throw error("config.yml", "language", "supported values are en and ru");
        }
        YamlConfiguration messages = loadFile(language.equals("en") ? "messages-en.yml" : "messages.yml");

        if (!general.contains("schema-version")) {
            throw error(general, "schema-version", "is required");
        }
        int schemaVersion = integer(general, "schema-version", 1, 1, 1);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw error("config.yml", "schema-version", "expected " + SUPPORTED_SCHEMA_VERSION);
        }

        String storageType = string(storage, "type");
        if (!storageType.equalsIgnoreCase("sqlite")) {
            throw error("storage.yml", "type", "only sqlite is supported by this build");
        }
        if (bool(punishments, "enabled", false)) {
            throw error("punishments.yml", "enabled", "automatic punishments are not available in the passive MVP");
        }
        Object punishmentActions = punishments.get("actions", List.of());
        if (!(punishmentActions instanceof List<?> actions)) {
            throw error("punishments.yml", "actions", "must be a list");
        } else if (!actions.isEmpty()) {
            throw error("punishments.yml", "actions", "automatic actions are not available in the passive MVP");
        }

        String databaseFile = string(storage, "file");
        if (databaseFile.contains("/") || databaseFile.contains("\\") || databaseFile.equals(".") || databaseFile.equals("..")) {
            throw error("storage.yml", "file", "must be a filename inside the plugin data directory");
        }

        Map<String, String> messageValues = new LinkedHashMap<>();
        for (String key : messages.getKeys(false)) {
            messageValues.put(key, string(messages, key));
        }
        for (String required : new String[]{
                "prefix", "no-permission", "unknown-command", "help", "alerts-enabled", "alerts-disabled",
                "debug-enabled", "debug-disabled", "debug-config-disabled", "player-not-found", "profile-empty", "profile-summary",
                "history-empty", "history-entry", "history-unavailable", "reload-success", "reload-failed", "performance", "invalid-usage"
        }) {
            if (!messageValues.containsKey(required)) {
                throw error(messages, required, "is required");
            }
        }

        double violationDecay = checks.contains("violations.decay-per-minute")
                ? decimal(checks, "violations.decay-per-minute", 0.25, 0.0, 100.0)
                : decimal(checks, "movement.decay-per-minute", 0.25, 0.0, 100.0);
        double alertThreshold = checks.contains("violations.alert-threshold")
                ? decimal(checks, "violations.alert-threshold", 3.0, 0.1, 1000.0)
                : decimal(checks, "movement.alert-threshold", 3.0, 0.1, 1000.0);

        return new PluginSettings(
                schemaVersion,
                language,
                integer(general, "storage.retention-days", 30, 0, 3650),
                databaseFile,
                integer(general, "storage.queue-capacity", 4096, 64, 65536),
                bool(general, "notifications.enabled", true),
                bool(general, "debug-notifications.enabled", false),
                bool(checks, "movement.enabled", true),
                decimal(checks, "movement.max-horizontal-speed", 18.0, 1.0, 100.0),
                integer(checks, "movement.minimum-sample-ms", 500, 100, 10000),
                decimal(checks, "movement.violation-increase", 1.0, 0.1, 100.0),
                violationDecay,
                alertThreshold,
                new PluginSettings.CombatSettings(
                        bool(checks, "combat.enabled", true),
                        decimal(checks, "combat.max-reach", 5.25, 3.0, 10.0),
                        decimal(checks, "combat.ping-compensation", 4.3, 0.0, 20.0),
                        decimal(checks, "combat.max-ping-compensation", 2.0, 0.0, 5.0),
                        decimal(checks, "combat.violation-increase", 1.0, 0.1, 100.0)
                ),
                new PluginSettings.ActionRateSettings(
                        bool(checks, "action-rate.enabled", true),
                        integer(checks, "action-rate.max-swings-per-second", 30, 1, 200),
                        integer(checks, "action-rate.max-interactions-per-second", 45, 1, 200),
                        decimal(checks, "action-rate.violation-increase", 0.5, 0.1, 100.0)
                ),
                new PluginSettings.BlockInteractionSettings(
                        bool(checks, "block-interaction.enabled", true),
                        integer(checks, "block-interaction.max-actions-per-second", 32, 1, 200),
                        decimal(checks, "block-interaction.violation-increase", 0.5, 0.1, 100.0)
                ),
                new PluginSettings.VehicleSettings(
                        bool(checks, "vehicle.enabled", true),
                        decimal(checks, "vehicle.max-horizontal-speed", 80.0, 5.0, 1000.0),
                        integer(checks, "vehicle.consecutive-samples", 2, 2, 10),
                        decimal(checks, "vehicle.violation-increase", 1.0, 0.1, 100.0)
                ),
                new PluginSettings.FlightSettings(
                        bool(checks, "flight.enabled", true),
                        decimal(checks, "flight.max-vertical-speed", 9.5, 1.0, 50.0),
                        integer(checks, "flight.consecutive-samples", 4, 2, 20),
                        decimal(checks, "flight.violation-increase", 1.0, 0.1, 100.0)
                ),
                new PluginSettings.PacketOrderSettings(
                        bool(checks, "packet-order.enabled", true),
                        integer(checks, "packet-order.mismatch-limit", 3, 1, 20),
                        integer(checks, "packet-order.mismatch-window-seconds", 30, 1, 600),
                        decimal(checks, "packet-order.violation-increase", 1.0, 0.1, 100.0)
                ),
                string(notifications, "alerts.permission"),
                string(notifications, "alerts.format"),
                string(notifications, "debug.permission"),
                string(notifications, "debug.format"),
                messageValues
        );
    }

    private YamlConfiguration loadFile(String name) throws SettingsException {
        File file = new File(dataFolder, name);
        if (!file.isFile()) {
            throw error(name, "", "file is missing");
        }

        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (Exception exception) {
            throw error(name, "", "cannot parse YAML: " + exception.getMessage());
        }
        configuration.set("__source_file", name);
        return configuration;
    }

    private int integer(YamlConfiguration config, String path, int fallback, int min, int max) throws SettingsException {
        Object value = config.get(path, fallback);
        if (!(value instanceof Number number)) {
            throw error(config, path, "must be an integer");
        }
        int result = number.intValue();
        if (result < min || result > max || number.doubleValue() != result) {
            throw error(config, path, "must be between " + min + " and " + max);
        }
        return result;
    }

    private double decimal(YamlConfiguration config, String path, double fallback, double min, double max) throws SettingsException {
        Object value = config.get(path, fallback);
        if (!(value instanceof Number number)) {
            throw error(config, path, "must be a number");
        }
        double result = number.doubleValue();
        if (!Double.isFinite(result) || result < min || result > max) {
            throw error(config, path, "must be between " + min + " and " + max);
        }
        return result;
    }

    private boolean bool(YamlConfiguration config, String path, boolean fallback) throws SettingsException {
        Object value = config.get(path, fallback);
        if (!(value instanceof Boolean result)) {
            throw error(config, path, "must be true or false");
        }
        return result;
    }

    private String string(YamlConfiguration config, String path) throws SettingsException {
        Object value = config.get(path);
        if (!(value instanceof String result) || result.isBlank()) {
            throw error(config, path, "must be a non-empty string");
        }
        return result;
    }

    private SettingsException error(YamlConfiguration config, String path, String reason) {
        String file = config.getString("__source_file", "settings");
        return error(file, path, reason);
    }

    private SettingsException error(String file, String path, String reason) {
        String key = path.isBlank() ? "file" : path;
        return new SettingsException(file + ": " + key + " " + reason);
    }
}
