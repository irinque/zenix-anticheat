package irinque.zenixac.config;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigurationManager {
    private final SettingsLoader loader;
    private final AtomicReference<PluginSettings> settings = new AtomicReference<>();

    public ConfigurationManager(File dataFolder) {
        loader = new SettingsLoader(dataFolder);
    }

    public PluginSettings get() {
        return settings.get();
    }

    public String message(String key) {
        return settings.get().messages().get(key);
    }

    public void load() throws SettingsException {
        settings.set(loader.load());
    }

    public void reload() throws SettingsException {
        PluginSettings next = loader.load();
        PluginSettings current = settings.get();
        if (current != null && (!current.databaseFile().equals(next.databaseFile())
                || current.retentionDays() != next.retentionDays()
                || current.queueCapacity() != next.queueCapacity())) {
            throw new SettingsException("storage.yml and storage.* settings require a server restart");
        }
        settings.set(next);
    }
}
