package io.github.offensichtlicher.patheticnavigation.processor;

import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.provider.NavigationPoint;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.provider.BukkitNavigationPoint;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Shared material lookup for the processors in this package.
 *
 * <p>The pathetic API only exposes {@link NavigationPoint#isTraversable()}, which is defined as
 * "the material is not solid". Anything that needs to tell water from air, or magma from stone,
 * has to unwrap the Bukkit implementation.
 *
 * @author Claude Opus 5
 */
public abstract class MaterialAwareProcessor {

    /**
     * Reads the material at a position.
     *
     * <p>Always uses the two argument overload. The single argument default on
     * {@link NavigationPointProvider} passes a {@code null} environment, and the providers shipped
     * with pathetic-bukkit resolve the world from exactly that argument — which is why
     * {@code PrioritizeMaterialsProcessor} cannot be used here.
     */
    protected @Nullable Material materialAt(@NotNull NavigationPointProvider provider,
                                            @NotNull PathPosition position,
                                            @Nullable EnvironmentContext environment) {
        return materialOf(provider.getNavigationPoint(position, environment));
    }

    /**
     * Whether the player would be swimming at this node.
     *
     * <p>True both when they are inside the liquid and when they are in the block directly above
     * it. The second case is what a surface swimmer looks like on a block grid, and without it the
     * shoreline is impassable: stepping off land into a lake lands on an air block whose feet are
     * neither on solid ground nor in the water yet.
     */
    protected boolean isSwimming(@NotNull NavigationPointProvider provider,
                                 @NotNull PathPosition position,
                                 @Nullable EnvironmentContext environment,
                                 @NotNull Set<Material> liquids) {
        Material feet = materialAt(provider, position, environment);
        if (feet != null && liquids.contains(feet)) {
            return true;
        }
        Material below = materialAt(provider, position.subtract(0, 1, 0), environment);
        return below != null && liquids.contains(below);
    }

    /**
     * Resolves the material behind a navigation point. Anything that is not a Bukkit point is
     * reported as unknown, and callers treat unknown as harmless.
     *
     * <p>Overridable so the processors can be exercised without a running server.
     */
    protected @Nullable Material materialOf(@NotNull NavigationPoint point) {
        return point instanceof BukkitNavigationPoint bukkitPoint ? bukkitPoint.getMaterial() : null;
    }
}
