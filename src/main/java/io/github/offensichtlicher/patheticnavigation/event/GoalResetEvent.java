package io.github.offensichtlicher.patheticnavigation.event;

import io.github.offensichtlicher.patheticnavigation.NavigatablePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a goal has been cleared. Reaching a goal does not fire this event,
 * {@link GoalReachedEvent} is used instead.
 */
public class GoalResetEvent extends NavigationEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Vector previousGoal;
    private final Reason reason;

    public GoalResetEvent(@NotNull Player player, @NotNull NavigatablePlayer navigatablePlayer,
                          @NotNull Vector previousGoal, @NotNull Reason reason) {
        super(player, navigatablePlayer);
        this.previousGoal = previousGoal;
        this.reason = reason;
    }

    /**
     * @return the goal that was active before the reset
     */
    public @NotNull Vector getPreviousGoal() {
        return previousGoal;
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
        /** Cleared through {@code /goal reset}. */
        COMMAND,
        /** Cleared programmatically through the library API. */
        API,
        /** Cleared because the player left the server. */
        DISCONNECT,
        /** Cleared because the plugin is shutting down. */
        SHUTDOWN
    }
}
