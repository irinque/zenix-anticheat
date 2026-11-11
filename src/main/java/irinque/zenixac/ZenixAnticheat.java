package irinque.zenixac;

import org.bukkit.plugin.java.JavaPlugin;

public final class ZenixAnticheat extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("[ZenixAC] Plugin is ready");
    }

    @Override
    public void onDisable() {
        getLogger().info("[ZenixAC] Plugin is off");
    }
}
