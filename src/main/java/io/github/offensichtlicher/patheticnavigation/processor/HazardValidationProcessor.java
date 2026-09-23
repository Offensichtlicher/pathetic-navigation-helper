package io.github.offensichtlicher.patheticnavigation.processor;

import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Rejects nodes that would route the player through or onto something that hurts.
 *
 * <p>Walkability alone is not enough for that. {@code NavigationPoint#isTraversable()} is defined
 * as "the material is not solid", so lava, fire, powder snow and sweet berry bushes all count as
 * traversable and a purely walkable path happily leads straight through them. Solid hazards have
 * the mirrored problem: magma blocks and campfires are solid, so they never appear in the body
 * column, but they make a perfectly acceptable floor as far as a walkable check is concerned.
 *
 * <p>This processor therefore inspects the block the player would stand on as well as every block
 * of their body column, and vetoes the node if any of them is a known hazard.
 *
 * <p>Runs on a pathfinding worker thread for every evaluated node, so it must stay allocation-light
 * and must not touch the Bukkit world API — it only reads the cached chunk data behind
 * {@link NavigationPointProvider}.
 *
 * @author Claude Opus 5
 */
public class HazardValidationProcessor extends MaterialAwareProcessor implements ValidationProcessor {

    /** Height of the body column that is checked, matching a player's 1.8 blocks. */
    public static final double DEFAULT_HEIGHT = 1.8;

    /**
     * Blocks that damage, trap or set the player on fire.
     */
    public static final Set<Material> DEFAULT_HAZARDS = Set.of(
            Material.LAVA,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.MAGMA_BLOCK,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.CACTUS,
            Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW,
            Material.WITHER_ROSE);

    private final double height;
    private final Set<Material> hazards;

    public HazardValidationProcessor() {
        this(DEFAULT_HEIGHT, DEFAULT_HAZARDS);
    }

    public HazardValidationProcessor(double height) {
        this(height, DEFAULT_HAZARDS);
    }

    public HazardValidationProcessor(double height, @NotNull Set<Material> hazards) {
        if (height < 0) {
            throw new IllegalArgumentException("height must not be negative: " + height);
        }
        this.height = height;
        this.hazards = Set.copyOf(hazards);
    }

    @Override
    public boolean isValid(EvaluationContext context) {
        NavigationPointProvider provider = context.getNavigationPointProvider();
        EnvironmentContext environment = context.getEnvironmentContext();
        PathPosition position = context.getCurrentPathPosition();

        // The floor the player would be standing on, which a walkable check only tests for solidity.
        if (isHazard(provider, position.subtract(0, 1, 0), environment)) {
            return false;
        }

        // The body column. Offset 0 reuses the position to avoid an allocation per evaluated node.
        if (isHazard(provider, position, environment)) {
            return false;
        }
        for (int offset = 1; offset <= height; offset++) {
            if (isHazard(provider, position.add(0, offset, 0), environment)) {
                return false;
            }
        }
        return true;
    }

    private boolean isHazard(NavigationPointProvider provider, PathPosition position, EnvironmentContext environment) {
        Material material = materialAt(provider, position, environment);
        return material != null && hazards.contains(material);
    }

    public @NotNull Set<Material> getHazards() {
        return hazards;
    }

    public double getHeight() {
        return height;
    }
}
