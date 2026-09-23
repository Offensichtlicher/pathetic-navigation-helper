package io.github.offensichtlicher.patheticnavigation.event;

import io.github.offensichtlicher.patheticnavigation.NavigatablePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a paused navigation starts rendering again.
 */
public class NavigationResumedEvent extends NavigationEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    public NavigationResumedEvent(@NotNull Player player, @NotNull NavigatablePlayer navigatablePlayer) {
        super(player, navigatablePlayer);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
