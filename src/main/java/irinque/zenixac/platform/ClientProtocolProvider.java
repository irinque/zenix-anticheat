package irinque.zenixac.platform;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public final class ClientProtocolProvider {
    private final Object viaApi;
    private final Method playerVersionMethod;

    private ClientProtocolProvider(Object viaApi, Method playerVersionMethod) {
        this.viaApi = viaApi;
        this.playerVersionMethod = playerVersionMethod;
    }

    public static ClientProtocolProvider create(Plugin plugin) {
        Plugin viaVersion = plugin.getServer().getPluginManager().getPlugin("ViaVersion");
        if (viaVersion == null || !viaVersion.isEnabled()) {
            plugin.getLogger().info("[ZenixAC] ViaVersion is not installed; client protocol will be recorded as unknown");
            return new ClientProtocolProvider(null, null);
        }

        try {
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via", true, viaVersion.getClass().getClassLoader());
            Object api = viaClass.getMethod("getAPI").invoke(null);
            Class<?> apiClass = Class.forName("com.viaversion.viaversion.api.ViaAPI", true, viaVersion.getClass().getClassLoader());
            Method getPlayerVersion = apiClass.getMethod("getPlayerVersion", UUID.class);
            plugin.getLogger().info("[ZenixAC] ViaVersion client protocol integration is available");
            return new ClientProtocolProvider(api, getPlayerVersion);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[ZenixAC] ViaVersion was found but its API could not be loaded; client protocol will be unknown",
                    exception);
            return new ClientProtocolProvider(null, null);
        }
    }

    public Integer getProtocol(UUID playerId) {
        if (viaApi == null) {
            return null;
        }
        try {
            Object protocol = playerVersionMethod.invoke(viaApi, playerId);
            if (protocol instanceof Number number && number.intValue() > 0) {
                return number.intValue();
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }
}
