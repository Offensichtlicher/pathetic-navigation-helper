package io.github.offensichtlicher.patheticnavigation.processor;

import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.processor.validation.WalkableProcessor;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Accepts a node if the player could either walk it or swim it.
 *
 * <p>Pathetic's {@link WalkableProcessor} requires solid ground below the node, so open water is
 * rejected and a lake becomes an impassable wall. That is too strict for guiding a player: they
 * can swim, it is just slower and more annoying than walking.
 *
 * <p>Validation processors are combined with AND, so a second processor could never loosen the
 * walkable rule. This one therefore wraps it and adds the swimming alternative instead of
 * reimplementing clearance, ground and step-up handling.
 *
 * <p>Swimming is only permitted, not encouraged — {@link SwimCostProcessor} is what keeps the path
 * on dry land whenever a reasonable detour exists.
 *
 * @author Claude Opus 5
 */
public class MovementValidationProcessor extends MaterialAwareProcessor implements ValidationProcessor {

    /** Body height the node has to accommodate, matching a player's 1.8 blocks. */
    public static final double DEFAULT_HEIGHT = 1.8;

    /** Liquids a player can cross under their own power. Lava is deliberately absent. */
    public static final Set<Material> DEFAULT_SWIMMABLE = Set.of(Material.WATER);

    private final double height;
    private final Set<Material> swimmable;
    private final WalkableProcessor walkable;

    public MovementValidationProcessor() {
        this(DEFAULT_HEIGHT, true, DEFAULT_SWIMMABLE);
    }

    public MovementValidationProcessor(double height, boolean allowStepUp) {
        this(height, allowStepUp, DEFAULT_SWIMMABLE);
    }

    public MovementValidationProcessor(double height, boolean allowStepUp, @NotNull Set<Material> swimmable) {
        if (height < 0) {
            throw new IllegalArgumentException("height must not be negative: " + height);
        }
        this.height = height;
        this.swimmable = Set.copyOf(swimmable);
        this.walkable = new WalkableProcessor(height, allowStepUp);
    }

    @Override
    public boolean isValid(EvaluationContext context) {
        return walkable.isValid(context) || canSwim(context);
    }

    /**
     * A node is swimmable when the player is in or on a liquid and their body still fits. The
     * liquid carries them, so no ground check applies.
     */
    private boolean canSwim(EvaluationContext context) {
        NavigationPointProvider provider = context.getNavigationPointProvider();
        EnvironmentContext environment = context.getEnvironmentContext();
        PathPosition position = context.getCurrentPathPosition();

        if (!isSwimming(provider, position, environment, swimmable)) {
            return false;
        }

        if (!provider.getNavigationPoint(position, environment).isTraversable()) {
            return false;
        }
        for (int offset = 1; offset <= height; offset++) {
            if (!provider.getNavigationPoint(position.add(0, offset, 0), environment).isTraversable()) {
                return false;
            }
        }
        return true;
    }

    public double getHeight() {
        return height;
    }

    public @NotNull Set<Material> getSwimmable() {
        return swimmable;
    }
}
