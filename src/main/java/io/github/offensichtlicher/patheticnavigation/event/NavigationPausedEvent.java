package io.github.offensichtlicher.patheticnavigation.event;

import io.github.offensichtlicher.patheticnavigation.NavigatablePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when rendering is suspended while the goal itself stays assigned. Happens when the
 * player moves beyond the configured pause range or leaves the goal's world.
 */
public class NavigationPausedEvent extends NavigationEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Reason reason;

    public NavigationPausedEvent(@NotNull Player player, @NotNull NavigatablePlayer navigatablePlayer, @NotNull Reason reason) {
        super(player, navigatablePlayer);
        this.reason = reason;
    }

    public @NotNull Reason getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    public enum Reason {
        /** The distance to the goal exceeds the configured pause range. */
        OUT_OF_RANGE,
        /** The player is no longer in the world the goal was set in. */
        WORLD_CHANGE,
        /** Paused programmatically through the library API. */
        API
    }
}
