package irinque.zenixac.config;

import java.io.File;
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
        loadFile("messages.yml");

        int schemaVersion = integer(general, "schema-version", 1, 1, 1);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw error("config.yml", "schema-version", "expected " + SUPPORTED_SCHEMA_VERSION);
        }

        String storageType = string(storage, "type");
        if (!storageType.equalsIgnoreCase("sqlite")) {
            throw error("storage.yml", "type", "only sqlite is supported by this build");
        }

        String databaseFile = string(storage, "file");
        if (databaseFile.contains("/") || databaseFile.contains("\\") || databaseFile.equals(".") || databaseFile.equals("..")) {
            throw error("storage.yml", "file", "must be a filename inside the plugin data directory");
        }

        return new PluginSettings(
                schemaVersion,
                integer(general, "storage.retention-days", 30, 0, 3650),
                databaseFile,
                integer(general, "storage.queue-capacity", 4096, 64, 65536),
                integer(general, "performance.sample-window-ms", 500, 100, 10000),
                bool(general, "notifications.enabled", true),
                bool(checks, "movement.enabled", true),
                decimal(checks, "movement.max-horizontal-speed", 18.0, 1.0, 100.0),
                integer(checks, "movement.minimum-sample-ms", 500, 100, 10000),
                decimal(checks, "movement.violation-increase", 1.0, 0.1, 100.0),
                decimal(checks, "movement.decay-per-minute", 0.25, 0.0, 100.0),
                decimal(checks, "movement.alert-threshold", 2.0, 0.1, 1000.0),
                bool(punishments, "enabled", false),
                string(notifications, "alerts.permission"),
                string(notifications, "alerts.format"),
                string(notifications, "debug.permission"),
                string(notifications, "debug.format")
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
