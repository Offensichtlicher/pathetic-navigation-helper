package io.github.offensichtlicher.patheticnavigation.particle;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import io.github.offensichtlicher.patheticnavigation.NavigationPath;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a line of soul fire flames along the path.
 * <p>
 * The raw path is a sequence of axis-aligned block steps, which on its own reads as a staircase of
 * right angles. Before drawing, the visible stretch is therefore rounded with Chaikin's corner
 * cutting, turning those corners into a continuous curve that still follows the path.
 * <p>
 * Particles are sent through {@link Player#spawnParticle}, so the packet reaches only the
 * navigating player — nearby players see nothing.
 * <p>
 * Every setting is mutable. Instances are handed out per player via {@link #copy()} and are only
 * touched from the main thread, so no synchronisation is needed.
 */
public class DefaultPathParticleRenderer implements PathParticleRenderer {

    /** Distance between two particles along the line, in blocks. */
    public static final double DEFAULT_SPACING = 0.5;
    /** Lifts the particles slightly off the ground so they are not swallowed by the block below. */
    public static final double DEFAULT_Y_OFFSET = 0.2;
    /** Corner cutting rounds per frame; each one doubles the number of intermediate points. */
    public static final int DEFAULT_SMOOTHING_PASSES = 3;
    /** Upper bound for {@link #setSmoothingPasses(int)}; beyond this the curve barely changes. */
    public static final int MAX_SMOOTHING_PASSES = 5;

    /**
     * Extra path collected past the visible end. Smoothing pulls a curve towards its neighbours,
     * so without this lead-in the tail of the line would wobble as the player walks.
     */
    private static final double SMOOTHING_PADDING = 2.0;

    /**
     * Rounding a corner shortens the line — a staircase of right angles collapses towards its
     * diagonal, which is the worst case at roughly 1/sqrt(2) of the original length. The raw
     * window is scaled up by that factor so the smoothed line still covers the full range.
     */
    private static final double SMOOTHING_SHRINK = 0.7;

    private Particle particle = Particle.SOUL_FIRE_FLAME;
    private byte floorInstructionRange = DEFAULT_FLOOR_INSTRUCTION_RANGE;
    private double spacing = DEFAULT_SPACING;
    private double yOffset = DEFAULT_Y_OFFSET;
    private int smoothingPasses = DEFAULT_SMOOTHING_PASSES;

    @Override
    public void render(@NotNull Player player, @NotNull NavigationPath path, int startIndex) {
        if (startIndex < 0 || startIndex >= path.size()) {
            return;
        }

        List<Vector> line = collectWindow(path, startIndex);
        for (int pass = 0; pass < smoothingPasses && line.size() > 2; pass++) {
            line = cutCorners(line);
        }
        draw(player, line);
    }

    /**
     * Collects the stretch of path that is about to be drawn, plus {@link #SMOOTHING_PADDING}
     * blocks of lead-in. Walking only this window keeps the cost independent of the total path
     * length.
     */
    private List<Vector> collectWindow(NavigationPath path, int startIndex) {
        double budget = floorInstructionRange / SMOOTHING_SHRINK + SMOOTHING_PADDING;
        List<Vector> window = new ArrayList<>();

        PathPosition previous = path.get(startIndex);
        window.add(NavigationPath.toVector(previous));

        double travelled = 0.0;
        for (int i = startIndex + 1; i < path.size() && travelled < budget; i++) {
            PathPosition current = path.get(i);
            travelled += previous.distance(current);
            window.add(NavigationPath.toVector(current));
            previous = current;
        }
        return window;
    }

    /**
     * One round of Chaikin's algorithm: every corner is replaced by two points at a quarter and
     * three quarters of its adjoining segments, which rounds it off. The endpoints are kept so the
     * line still starts at the player's feet.
     */
    private static List<Vector> cutCorners(List<Vector> points) {
        List<Vector> cut = new ArrayList<>(points.size() * 2);
        cut.add(points.get(0));
        for (int i = 0; i < points.size() - 1; i++) {
            Vector from = points.get(i);
            Vector to = points.get(i + 1);
            cut.add(lerp(from, to, 0.25));
            cut.add(lerp(from, to, 0.75));
        }
        cut.add(points.get(points.size() - 1));
        return cut;
    }

    /**
     * Walks the smoothed polyline and drops a particle every {@link #getSpacing()} blocks, until
     * {@link #getFloorInstructionRange()} blocks have been covered.
     */
    private void draw(Player player, List<Vector> line) {
        Vector previous = line.get(0);
        spawn(player, previous.getX(), previous.getY(), previous.getZ());
        if (line.size() < 2) {
            return;
        }

        double range = floorInstructionRange;
        double travelled = 0.0;
        double nextAt = spacing;

        for (int i = 1; i < line.size(); i++) {
            Vector current = line.get(i);
            double segment = previous.distance(current);
            if (segment <= 1.0E-6) {
                continue;
            }

            while (nextAt <= travelled + segment) {
                if (nextAt > range) {
                    return;
                }
                double ratio = (nextAt - travelled) / segment;
                spawn(player,
                        previous.getX() + (current.getX() - previous.getX()) * ratio,
                        previous.getY() + (current.getY() - previous.getY()) * ratio,
                        previous.getZ() + (current.getZ() - previous.getZ()) * ratio);
                nextAt += spacing;
            }

            travelled += segment;
            if (travelled >= range) {
                return;
            }
            previous = current;
        }
    }

    private void spawn(Player player, double x, double y, double z) {
        // count = 1, no offset, no extra, force = true so the line stays visible on reduced
        // client particle settings.
        player.spawnParticle(particle, x, y + yOffset, z, 1, 0.0, 0.0, 0.0, 0.0, null, true);
    }

    private static Vector lerp(Vector from, Vector to, double ratio) {
        return new Vector(
                from.getX() + (to.getX() - from.getX()) * ratio,
                from.getY() + (to.getY() - from.getY()) * ratio,
                from.getZ() + (to.getZ() - from.getZ()) * ratio);
    }

    @Override
    public @NotNull PathParticleRenderer copy() {
        DefaultPathParticleRenderer copy = new DefaultPathParticleRenderer();
        copy.particle = particle;
        copy.floorInstructionRange = floorInstructionRange;
        copy.spacing = spacing;
        copy.yOffset = yOffset;
        copy.smoothingPasses = smoothingPasses;
        return copy;
    }

    public @NotNull Particle getParticle() {
        return particle;
    }

    /**
     * Note that a coloured particle such as {@link Particle#DUST} needs a data object, which this
     * renderer does not send. Implement {@link PathParticleRenderer} yourself for that case.
     */
    public void setParticle(@NotNull Particle particle) {
        this.particle = particle;
    }

    public byte getFloorInstructionRange() {
        return floorInstructionRange;
    }

    /**
     * @param range how many blocks of path to draw ahead of the player; clamped to at least 1
     */
    public void setFloorInstructionRange(byte range) {
        this.floorInstructionRange = (byte) Math.max(1, range);
    }

    public double getSpacing() {
        return spacing;
    }

    public void setSpacing(double spacing) {
        if (spacing <= 0.0) {
            throw new IllegalArgumentException("spacing must be positive");
        }
        this.spacing = spacing;
    }

    public double getYOffset() {
        return yOffset;
    }

    public void setYOffset(double yOffset) {
        this.yOffset = yOffset;
    }

    public int getSmoothingPasses() {
        return smoothingPasses;
    }

    /**
     * @param smoothingPasses rounds of corner cutting; 0 draws the raw block-step path,
     *                        clamped to {@link #MAX_SMOOTHING_PASSES}
     */
    public void setSmoothingPasses(int smoothingPasses) {
        this.smoothingPasses = Math.clamp(smoothingPasses, 0, MAX_SMOOTHING_PASSES);
    }
}
