package irinque.zenixac;

import irinque.zenixac.config.ConfigurationManager;
import irinque.zenixac.config.SettingsException;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZenixAnticheat extends JavaPlugin {
    private ConfigurationManager configurationManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("checks.yml", false);
        saveResource("notifications.yml", false);
        saveResource("punishments.yml", false);
        saveResource("storage.yml", false);
        saveResource("messages.yml", false);

        configurationManager = new ConfigurationManager(getDataFolder());
        try {
            configurationManager.load();
        } catch (SettingsException exception) {
            getLogger().severe("[ZenixAC] Configuration error: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("[ZenixAC] Plugin is ready");
    }

    @Override
    public void onDisable() {
        getLogger().info("[ZenixAC] Plugin is off");
    }

    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }
}
