package io.github.offensichtlicher.patheticnavigation.bossbar;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Shows the current goal and the direction to follow as a boss bar — the text alternative to the
 * particle line.
 * <p>
 * Like the particle renderer, a navigator never decides whether it should be visible;
 * {@link io.github.offensichtlicher.patheticnavigation.NavigatablePlayer} calls {@link #update}
 * while a goal is active and {@link #hide} as soon as the navigation is paused, cleared or closed.
 * <p>
 * Both methods are always invoked on the main thread. Adventure's boss bar implementation is not
 * thread-safe, so implementations must not be driven from anywhere else.
 */
public interface BossBarNavigator {

    /**
     * @param player    the viewer
     * @param goal      the active goal
     * @param direction where the player has to turn, relative to their view
     * @param progress  how far the player has come, between 0 and 1
     */
    void update(@NotNull Player player, @NotNull Vector goal, @NotNull Direction direction, float progress);

    /**
     * Hides the bar. Must be safe to call when nothing is shown.
     */
    void hide(@NotNull Player player);

    /**
     * @return an independent copy; every player needs their own bar, a shared one would push
     * one player's progress to all viewers
     */
    @NotNull BossBarNavigator copy();
}
