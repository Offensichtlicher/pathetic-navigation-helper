package io.github.offensichtlicher.patheticnavigation.event;

import io.github.offensichtlicher.patheticnavigation.NavigatablePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when the player enters the arrival radius of the current goal. The goal is cleared
 * right after this event; no {@link GoalResetEvent} follows.
 */
public class GoalReachedEvent extends NavigationEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Vector goal;

    public GoalReachedEvent(@NotNull Player player, @NotNull NavigatablePlayer navigatablePlayer, @NotNull Vector goal) {
        super(player, navigatablePlayer);
        this.goal = goal;
    }

    public @NotNull Vector getGoal() {
        return goal;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
