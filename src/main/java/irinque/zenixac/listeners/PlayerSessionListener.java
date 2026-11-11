package irinque.zenixac.listeners;

import irinque.zenixac.notification.StaffNotifier;
import irinque.zenixac.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerSessionListener implements Listener {
    private final SessionManager sessionManager;
    private final StaffNotifier staffNotifier;

    public PlayerSessionListener(SessionManager sessionManager, StaffNotifier staffNotifier) {
        this.sessionManager = sessionManager;
        this.staffNotifier = staffNotifier;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        register(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        staffNotifier.untrack(player);
        sessionManager.end(player);
    }

    public void register(Player player) {
        staffNotifier.track(player);
        sessionManager.start(player);
    }
}
