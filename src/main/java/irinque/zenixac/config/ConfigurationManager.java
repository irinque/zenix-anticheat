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

    public void load() throws SettingsException {
        settings.set(loader.load());
    }

    public void reload() throws SettingsException {
        PluginSettings next = loader.load();
        settings.set(next);
    }
}
