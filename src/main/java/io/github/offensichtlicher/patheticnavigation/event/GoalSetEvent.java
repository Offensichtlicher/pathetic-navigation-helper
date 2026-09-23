package io.github.offensichtlicher.patheticnavigation.event;

import io.github.offensichtlicher.patheticnavigation.NavigatablePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a goal is assigned to a player. Cancelling keeps the previous goal untouched.
 * The goal may be rewritten by a listener via {@link #setGoal(Vector)}.
 */
public class GoalSetEvent extends NavigationEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private Vector goal;
    private boolean cancelled;

    public GoalSetEvent(@NotNull Player player, @NotNull NavigatablePlayer navigatablePlayer, @NotNull Vector goal) {
        super(player, navigatablePlayer);
        this.goal = goal;
    }

    public @NotNull Vector getGoal() {
        return goal;
    }

    public void setGoal(@NotNull Vector goal) {
        this.goal = goal;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
