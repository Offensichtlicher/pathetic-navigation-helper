package io.github.offensichtlicher.patheticnavigation;

import io.github.offensichtlicher.patheticnavigation.event.GoalResetEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps the registry in sync with the players actually on the server.
 */
public class IntegrityListener implements Listener {

    private final NavigationRegistry registry;

    public IntegrityListener(NavigationRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void handleDisconnect(PlayerQuitEvent event) {
        NavigatablePlayer navigatablePlayer = registry.remove(event.getPlayer().getUniqueId());
        if (navigatablePlayer != null) {
            navigatablePlayer.close(GoalResetEvent.Reason.DISCONNECT);
        }
    }

    @EventHandler
    public void handleWorldChange(PlayerChangedWorldEvent event) {
        NavigatablePlayer navigatablePlayer = registry.get(event.getPlayer().getUniqueId());
        if (navigatablePlayer != null) {
            navigatablePlayer.handleWorldChange();
        }
    }
}
