package irinque.zenixac.platform;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.IllegalPluginAccessException;

public final class PlatformScheduler {
    private final Plugin plugin;
    private final Logger logger;
    private final boolean folia;
    private final Method entitySchedulerMethod;
    private final Method executeMethod;

    public PlatformScheduler(Plugin plugin) throws IllegalStateException {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.folia = detectFolia();

        if (!folia) {
            entitySchedulerMethod = null;
            executeMethod = null;
            return;
        }

        try {
            entitySchedulerMethod = Player.class.getMethod("getScheduler");
            executeMethod = entitySchedulerMethod.getReturnType().getMethod(
                    "execute", Plugin.class, Runnable.class, Runnable.class, long.class
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Folia entity scheduler API is unavailable", exception);
        }
    }

    public boolean isFolia() {
        return folia;
    }

    public void executeForPlayer(Player player, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (!folia) {
            try {
                Bukkit.getScheduler().runTask(plugin, task);
            } catch (IllegalPluginAccessException exception) {
                if (plugin.isEnabled()) {
                    logger.log(Level.WARNING, "[ZenixAC] Could not schedule work for a player", exception);
                }
            }
            return;
        }

        try {
            Object scheduler = entitySchedulerMethod.invoke(player);
            executeMethod.invoke(scheduler, plugin, task, null, 1L);
        } catch (ReflectiveOperationException exception) {
            logger.log(Level.WARNING, "[ZenixAC] Could not schedule work for a Folia player", exception);
        }
    }

    private boolean detectFolia() {
        try {
            ClassLoader classLoader = plugin.getClass().getClassLoader();
            Class<?> buildInfoType = Class.forName("io.papermc.paper.ServerBuildInfo", false, classLoader);
            Object buildInfo = buildInfoType.getMethod("buildInfo").invoke(null);
            Class<?> keyType = Class.forName("net.kyori.adventure.key.Key", false, classLoader);
            Object foliaKey = keyType.getMethod("key", String.class, String.class).invoke(null, "papermc", "folia");
            return (boolean) buildInfoType.getMethod("isBrandCompatible", keyType).invoke(buildInfo, foliaKey);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            if (plugin.getServer().getName().equalsIgnoreCase("Folia")) {
                return true;
            }
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, plugin.getClass().getClassLoader());
                return true;
            } catch (ClassNotFoundException | LinkageError exception) {
                return false;
            }
        }
    }
}
