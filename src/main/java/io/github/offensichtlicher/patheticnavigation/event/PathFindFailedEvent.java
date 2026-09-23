package io.github.offensichtlicher.patheticnavigation.event;

import de.bsommerfeld.pathetic.api.pathing.result.PathState;
import io.github.offensichtlicher.patheticnavigation.NavigatablePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Fired when a pathfinding attempt did not yield a usable path. The goal stays assigned;
 * the next tick will retry once the recompute cooldown has elapsed.
 * <p>
 * The search itself runs on a pathetic worker thread, but this event is dispatched on the
 * main thread like every other navigation event.
 */
public class PathFindFailedEvent extends NavigationEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final PathState pathState;
    private final Throwable throwable;

    public PathFindFailedEvent(@NotNull Player player, @NotNull NavigatablePlayer navigatablePlayer,
                               @NotNull PathState pathState, @Nullable Throwable throwable) {
        super(player, navigatablePlayer);
        this.pathState = pathState;
        this.throwable = throwable;
    }

    public @NotNull PathState getPathState() {
        return pathState;
    }

    /**
     * @return the exception that aborted the search, or {@code null} if the search simply found nothing
     */
    public @Nullable Throwable getThrowable() {
        return throwable;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
