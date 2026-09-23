package io.github.offensichtlicher.patheticnavigation;

import de.bsommerfeld.pathetic.api.pathing.result.Path;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * An immutable snapshot of a pathetic {@link Path}, materialised into an indexable list.
 * <p>
 * {@link Path} is only {@link Iterable}, but both the particle renderer and the boss bar need
 * random access to walk the path from the player's current position. Collecting once per search
 * is cheaper than iterating the whole path on every tick.
 * <p>
 * Instances are immutable and therefore safe to publish from a pathfinding worker thread to the
 * main thread through a volatile field.
 *
 * @author Claude Opus 5
 */
public final class NavigationPath {

    private final List<PathPosition> positions;

    private NavigationPath(List<PathPosition> positions) {
        this.positions = positions;
    }

    /**
     * @return a snapshot of the given path, or {@code null} if it holds fewer than two positions
     */
    public static @Nullable NavigationPath of(@Nullable Path path) {
        if (path == null) {
            return null;
        }
        Collection<PathPosition> collected = path.collect();
        if (collected == null || collected.size() < 2) {
            return null;
        }
        return new NavigationPath(List.copyOf(collected));
    }

    public int size() {
        return positions.size();
    }

    public @NotNull PathPosition get(int index) {
        return positions.get(index);
    }

    public @NotNull List<PathPosition> positions() {
        return positions;
    }

    /**
     * Finds the path node closest to {@code location}, searching only within {@code window}
     * nodes around {@code hint}. Keeping the search local turns the per-tick cost into a constant
     * instead of scanning the whole path.
     *
     * @param hint the index returned by the previous call
     */
    public int nearestIndex(@NotNull Vector location, int hint, int window) {
        int from = Math.max(0, hint - window);
        int to = Math.min(positions.size(), hint + window + 1);
        int best = from;
        double bestDistance = Double.MAX_VALUE;
        for (int i = from; i < to; i++) {
            double distance = distanceSquared(location, positions.get(i));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /**
     * @return the distance between {@code location} and the node at {@code index}
     */
    public double distanceTo(@NotNull Vector location, int index) {
        if (index < 0 || index >= positions.size()) {
            return Double.MAX_VALUE;
        }
        return Math.sqrt(distanceSquared(location, positions.get(index)));
    }

    /**
     * Walks {@code lookaheadBlocks} along the path and returns the position reached.
     *
     * @return the look-ahead point, or {@code null} if the path ends before that distance
     */
    public @Nullable Vector pointAhead(int fromIndex, double lookaheadBlocks) {
        if (fromIndex < 0 || fromIndex >= positions.size()) {
            return null;
        }
        double remaining = lookaheadBlocks;
        PathPosition previous = positions.get(fromIndex);
        for (int i = fromIndex + 1; i < positions.size(); i++) {
            PathPosition current = positions.get(i);
            double segment = previous.distance(current);
            if (segment >= remaining) {
                return toVector(current);
            }
            remaining -= segment;
            previous = current;
        }
        return null;
    }

    /**
     * Path nodes are block coordinates, so they are compared against the block centre.
     */
    public static @NotNull Vector toVector(@NotNull PathPosition position) {
        return new Vector(position.getCenteredX(), position.getY(), position.getCenteredZ());
    }

    private static double distanceSquared(Vector location, PathPosition position) {
        double dx = location.getX() - position.getCenteredX();
        double dy = location.getY() - position.getY();
        double dz = location.getZ() - position.getCenteredZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
