package io.github.offensichtlicher.patheticnavigation.particle;

import io.github.offensichtlicher.patheticnavigation.NavigationPath;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the walkable path on the ground for a single player.
 * <p>
 * A renderer never decides <em>whether</em> to render — that is
 * {@link io.github.offensichtlicher.patheticnavigation.NavigatablePlayer}'s job, which only calls
 * {@link #render} when particles are enabled and the navigation is neither paused nor goal-less.
 * <p>
 * {@link #render} is always invoked on the main thread.
 */
public interface PathParticleRenderer {

    /**
     * Default length of the rendered line, in blocks of path travelled.
     */
    byte DEFAULT_FLOOR_INSTRUCTION_RANGE = 16;

    /**
     * Renders the next stretch of the path.
     *
     * @param player     the only player that may see these particles
     * @param path       the currently active path
     * @param startIndex the node closest to the player; rendering starts here
     */
    void render(@NotNull Player player, @NotNull NavigationPath path, int startIndex);

    /**
     * @return an independent copy carrying the same settings, so every player can be tuned separately
     */
    @NotNull PathParticleRenderer copy();
}
