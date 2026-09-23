package io.github.offensichtlicher.patheticnavigation.event;

import io.github.offensichtlicher.patheticnavigation.NavigatablePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for every event fired by the navigation library.
 * <p>
 * All navigation events are synchronous and are always fired on the server's main thread,
 * even when the action that caused them originated on a pathfinding worker thread.
 * Listeners may therefore use the Bukkit API without any further scheduling.
 */
public abstract class NavigationEvent extends Event {

    private final Player player;
    private final NavigatablePlayer navigatablePlayer;

    protected NavigationEvent(@NotNull Player player, @NotNull NavigatablePlayer navigatablePlayer) {
        super(false);
        this.player = player;
        this.navigatablePlayer = navigatablePlayer;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull NavigatablePlayer getNavigatablePlayer() {
        return navigatablePlayer;
    }
}
