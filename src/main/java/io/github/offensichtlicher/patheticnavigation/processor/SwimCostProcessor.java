package io.github.offensichtlicher.patheticnavigation.processor;

import de.bsommerfeld.pathetic.api.pathing.processing.Cost;
import de.bsommerfeld.pathetic.api.pathing.processing.CostProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Makes swimming expensive so the path stays on dry land whenever that is remotely reasonable.
 *
 * <p>{@link MovementValidationProcessor} allows water, this decides how much the pathfinder dislikes
 * it. The penalty is added per node spent in a liquid, so a short swim across a river still wins
 * over a long walk around it, while a lake gets circled.
 *
 * @author Claude Opus 5
 */
public class SwimCostProcessor extends MaterialAwareProcessor implements CostProcessor {

    /**
     * Extra cost per node spent in water. Roughly speaking the path accepts a detour of this many
     * blocks to avoid one block of swimming.
     */
    public static final double DEFAULT_PENALTY = 4.0;

    /** Liquids that count as swimming. Matches {@link MovementValidationProcessor#DEFAULT_SWIMMABLE}. */
    public static final Set<Material> DEFAULT_LIQUIDS = Set.of(Material.WATER);

    private final double penalty;
    private final Set<Material> liquids;

    public SwimCostProcessor() {
        this(DEFAULT_PENALTY, DEFAULT_LIQUIDS);
    }

    public SwimCostProcessor(double penalty) {
        this(penalty, DEFAULT_LIQUIDS);
    }

    public SwimCostProcessor(double penalty, @NotNull Set<Material> liquids) {
        if (penalty < 0) {
            throw new IllegalArgumentException("penalty must not be negative: " + penalty);
        }
        this.penalty = penalty;
        this.liquids = Set.copyOf(liquids);
    }

    @Override
    public Cost calculateCostContribution(EvaluationContext context) {
        // Same predicate the validator uses, so surface swimming is charged just like being
        // submerged — otherwise the path would happily skim across a lake for free.
        boolean swimming = isSwimming(
                context.getNavigationPointProvider(),
                context.getCurrentPathPosition(),
                context.getEnvironmentContext(),
                liquids);
        return swimming ? Cost.of(penalty) : Cost.ZERO;
    }

    public double getPenalty() {
        return penalty;
    }

    public @NotNull Set<Material> getLiquids() {
        return liquids;
    }
}
