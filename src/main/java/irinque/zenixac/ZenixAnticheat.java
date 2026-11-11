package irinque.zenixac;

import irinque.zenixac.config.ConfigurationManager;
import irinque.zenixac.config.SettingsException;
import irinque.zenixac.checks.MovementCheck;
import irinque.zenixac.checks.ViolationManager;
import irinque.zenixac.commands.ZenixCommand;
import irinque.zenixac.listeners.PlayerSessionListener;
import irinque.zenixac.notification.StaffNotifier;
import irinque.zenixac.platform.ClientProtocolProvider;
import irinque.zenixac.platform.PlatformScheduler;
import irinque.zenixac.session.SessionManager;
import irinque.zenixac.storage.ViolationService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZenixAnticheat extends JavaPlugin {
    private ConfigurationManager configurationManager;
    private ViolationService violationService;
    private SessionManager sessionManager;
    private PlatformScheduler scheduler;
    private StaffNotifier staffNotifier;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("checks.yml", false);
        saveResource("notifications.yml", false);
        saveResource("punishments.yml", false);
        saveResource("storage.yml", false);
        saveResource("messages.yml", false);
        saveResource("messages-en.yml", false);

        configurationManager = new ConfigurationManager(getDataFolder());
        try {
            configurationManager.load();
        } catch (SettingsException exception) {
            getLogger().severe("[ZenixAC] Configuration error: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            scheduler = new PlatformScheduler(this);
        } catch (IllegalStateException exception) {
            getLogger().severe("[ZenixAC] Unsupported server scheduler: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        var settings = configurationManager.get();
        violationService = new ViolationService(
                new java.io.File(getDataFolder(), settings.databaseFile()),
                settings.retentionDays(),
                settings.queueCapacity(),
                getLogger()
        );
        sessionManager = new SessionManager(violationService);
        ClientProtocolProvider protocolProvider = ClientProtocolProvider.create(this);
        staffNotifier = new StaffNotifier(configurationManager, scheduler);
        ViolationManager violationManager = new ViolationManager(
                configurationManager, violationService, sessionManager, protocolProvider, staffNotifier
        );

        getServer().getPluginManager().registerEvents(new PlayerSessionListener(sessionManager, staffNotifier), this);
        getServer().getPluginManager().registerEvents(new MovementCheck(configurationManager, violationManager), this);
        ZenixCommand command = new ZenixCommand(
                this, configurationManager, violationService, staffNotifier, scheduler
        );
        getCommand("zenixac").setExecutor(command);
        getCommand("zenixac").setTabCompleter(command);
        for (Player player : getServer().getOnlinePlayers()) {
            scheduler.executeForPlayer(player, () -> {
                staffNotifier.track(player);
                sessionManager.start(player);
            });
        }

        if (scheduler.isFolia()) {
            getLogger().info("[ZenixAC] Folia-compatible entity scheduling is active");
        }
        getLogger().info("[ZenixAC] Plugin is ready");
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            sessionManager.close();
        }
        if (violationService != null) {
            violationService.close();
        }
        getLogger().info("[ZenixAC] Plugin is off");
    }

    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }
}
