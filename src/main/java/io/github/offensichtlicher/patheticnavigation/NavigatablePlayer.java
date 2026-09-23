package io.github.offensichtlicher.patheticnavigation;

import de.bsommerfeld.pathetic.api.pathing.Pathfinder;
import de.bsommerfeld.pathetic.api.pathing.PathfindingSearch;
import de.bsommerfeld.pathetic.api.pathing.result.PathState;
import de.bsommerfeld.pathetic.api.pathing.result.PathfinderResult;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.context.BukkitEnvironmentContext;
import io.github.offensichtlicher.patheticnavigation.bossbar.BossBarNavigator;
import io.github.offensichtlicher.patheticnavigation.bossbar.Direction;
import io.github.offensichtlicher.patheticnavigation.event.GoalReachedEvent;
import io.github.offensichtlicher.patheticnavigation.event.GoalResetEvent;
import io.github.offensichtlicher.patheticnavigation.event.GoalSetEvent;
import io.github.offensichtlicher.patheticnavigation.event.NavigationPausedEvent;
import io.github.offensichtlicher.patheticnavigation.event.NavigationResumedEvent;
import io.github.offensichtlicher.patheticnavigation.event.PathFindFailedEvent;
import io.github.offensichtlicher.patheticnavigation.particle.PathParticleRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Guides a single player towards a goal, drawing a particle line and a boss bar along the way.
 *
 * <h2>Threading</h2>
 * Every public method may be called from any thread. Anything that touches the Bukkit API is
 * routed onto the main thread through the global region scheduler, so callers never have to
 * schedule themselves. Consequently {@link #setGoal} and friends complete asynchronously when
 * called off the main thread; observe the outcome through the events in
 * {@link io.github.offensichtlicher.patheticnavigation.event} rather than by polling.
 * <p>
 * Pathfinding itself runs on pathetic's own worker pool. Those callbacks only publish the result
 * through volatile state and never touch the Bukkit API — {@link #tick()} picks it up on the next
 * main thread tick.
 */
public class NavigatablePlayer {

    /** Beyond this distance to the goal rendering pauses; a value of 0 or less disables pausing. */
    public static final double DEFAULT_PAUSE_BEYOND_RANGE = 512.0;
    /** Minimum time between two pathfinding searches. */
    public static final long DEFAULT_RECOMPUTE_COOLDOWN_MS = 1_500L;
    /** How far the player may stray from the path before it is recomputed. */
    public static final double DEFAULT_RECOMPUTE_DEVIATION = 3.0;
    /** Distance at which the goal counts as reached. */
    public static final double DEFAULT_ARRIVAL_RADIUS = 2.0;
    /** How far along the path the boss bar arrow points. */
    public static final double DEFAULT_DIRECTION_LOOKAHEAD = 8.0;

    /** Nodes searched around the previous index when re-locating the player on the path. */
    private static final int NEAREST_INDEX_WINDOW = 24;

    private final Plugin plugin;
    private final Pathfinder pathfinder;

    private volatile @Nullable Player player;
    private volatile boolean particle = true;
    private volatile boolean bossBar = true;
    private volatile @Nullable PathParticleRenderer pathParticleRenderer;
    private volatile @Nullable BossBarNavigator bossBarNavigator;

    private volatile @Nullable Vector currentGoal;
    /** Captured when the goal is set; required to detect world changes and to resume afterwards. */
    private volatile @Nullable UUID worldIdSnapshot;
    private volatile boolean paused;
    /** Distance at the moment the goal was set, used as the boss bar's 0% mark. */
    private volatile double initialDistance = 1.0;

    private final AtomicReference<NavigationPath> activePath = new AtomicReference<>();
    private final AtomicBoolean searchInProgress = new AtomicBoolean();
    private final AtomicLong searchGeneration = new AtomicLong();
    private volatile @Nullable PathfindingSearch currentSearch;
    private volatile long lastComputeMs;

    /** Main thread only: the path instance the index below refers to. */
    private @Nullable NavigationPath renderedPath;
    /** Main thread only: the node the player is currently closest to. */
    private int lastPathIndex;

    private volatile double pauseBeyondRange = DEFAULT_PAUSE_BEYOND_RANGE;
    private volatile long recomputeCooldownMs = DEFAULT_RECOMPUTE_COOLDOWN_MS;
    private volatile double recomputeDeviation = DEFAULT_RECOMPUTE_DEVIATION;
    private volatile double arrivalRadius = DEFAULT_ARRIVAL_RADIUS;
    private volatile double directionLookahead = DEFAULT_DIRECTION_LOOKAHEAD;

    private volatile @Nullable Consumer<Player> pathFailCallback =
            player -> player.sendActionBar(Component.text("Navigation failed!", NamedTextColor.RED));

    protected NavigatablePlayer(@NotNull Plugin plugin, @NotNull Player player, @NotNull Pathfinder pathfinder) {
        this.plugin = plugin;
        this.player = player;
        this.pathfinder = pathfinder;
    }

    /**
     * @return the navigation state of the given player, creating it on first use
     */
    public static @NotNull NavigatablePlayer of(@NotNull Player player) {
        return NavigationHelperPlugin.get().getPlayer(player);
    }

    // ------------------------------------------------------------------ goal

    /**
     * Assigns a new goal, replacing any previous one. Passing {@code null} clears it.
     * <p>
     * Fires {@link GoalSetEvent}, which may rewrite or cancel the goal.
     */
    public void setGoal(@Nullable Vector goalCoordinates) {
        if (goalCoordinates == null) {
            clearGoal();
            return;
        }
        Vector goal = goalCoordinates.clone();
        runOnMain(() -> applyGoal(goal));
    }

    private synchronized void applyGoal(Vector goal) {
        Player player = this.player;
        if (player == null) {
            return;
        }

        GoalSetEvent event = new GoalSetEvent(player, this, goal);
        if (!event.callEvent()) {
            return;
        }
        Vector accepted = event.getGoal().clone();

        abortSearch();
        currentGoal = accepted;
        worldIdSnapshot = player.getWorld().getUID();
        paused = false;
        activePath.set(null);
        renderedPath = null;
        lastPathIndex = 0;
        lastComputeMs = 0L;

        Vector here = player.getLocation().toVector();
        initialDistance = Math.max(1.0, here.distance(accepted));
        requestPath(player, here);
    }

    /**
     * Clears the goal and fires {@link GoalResetEvent} with {@link GoalResetEvent.Reason#API}.
     */
    public void clearGoal() {
        clearGoal(GoalResetEvent.Reason.API);
    }

    public void clearGoal(GoalResetEvent.@NotNull Reason reason) {
        runOnMain(() -> clearGoalInternal(reason));
    }

    private synchronized void clearGoalInternal(GoalResetEvent.Reason reason) {
        Vector previous = currentGoal;
        if (previous == null) {
            return;
        }
        Player player = this.player;
        resetState();
        if (player != null) {
            hideBossBar(player);
            new GoalResetEvent(player, this, previous, reason).callEvent();
        }
    }

    public @Nullable Vector getGoal() {
        Vector goal = currentGoal;
        return goal == null ? null : goal.clone();
    }

    public boolean isPaused() {
        return paused;
    }

    // --------------------------------------------------------------- pausing

    /**
     * Suspends rendering while keeping the goal, e.g. after a long distance teleport or a world
     * switch. Rendering resumes by itself once the player is back within
     * {@link #getPauseBeyondRange()} of the goal in the goal's world.
     */
    public void pauseNavigation() {
        runOnMain(() -> pauseNavigation(NavigationPausedEvent.Reason.API));
    }

    private synchronized void pauseNavigation(NavigationPausedEvent.Reason reason) {
        if (paused) {
            return;
        }
        Player player = this.player;
        if (player == null || currentGoal == null) {
            return;
        }
        paused = true;
        abortSearch();
        activePath.set(null);
        renderedPath = null;
        lastPathIndex = 0;
        hideBossBar(player);
        new NavigationPausedEvent(player, this, reason).callEvent();
    }

    public void resumeNavigation() {
        runOnMain(this::resumeNavigationInternal);
    }

    private synchronized void resumeNavigationInternal() {
        Player player = this.player;
        if (player == null || currentGoal == null || !paused) {
            return;
        }
        if (!player.getWorld().getUID().equals(worldIdSnapshot)) {
            return;
        }

        paused = false;
        lastComputeMs = 0L;
        Vector here = player.getLocation().toVector();
        initialDistance = Math.max(1.0, here.distance(currentGoal));
        requestPath(player, here);
        new NavigationResumedEvent(player, this).callEvent();
    }

    /**
     * Pauses when the player left the goal's world, resumes when they came back.
     */
    protected void handleWorldChange() {
        runOnMain(() -> {
            Player player = this.player;
            if (player == null || worldIdSnapshot == null) {
                return;
            }
            if (player.getWorld().getUID().equals(worldIdSnapshot)) {
                resumeNavigationInternal();
            } else {
                pauseNavigation(NavigationPausedEvent.Reason.WORLD_CHANGE);
            }
        });
    }

    // ------------------------------------------------------------ pathfinding

    /**
     * Starts a search unless one is already running. Never blocks.
     */
    private void requestPath(Player player, Vector from) {
        Vector goal = currentGoal;
        if (goal == null || !searchInProgress.compareAndSet(false, true)) {
            return;
        }

        long generation = searchGeneration.incrementAndGet();
        lastComputeMs = System.currentTimeMillis();
        try {
            currentSearch = pathfinder.findPath(
                            PathPosition.of(from.getX(), from.getY(), from.getZ()),
                            PathPosition.of(goal.getX(), goal.getY(), goal.getZ()),
                            new BukkitEnvironmentContext(player.getWorld()))
                    .ifPresent(result -> onPathResult(generation, result))
                    .orElse(result -> onPathFailure(generation, result))
                    .exceptionally(throwable -> onPathError(generation, throwable));
        } catch (RuntimeException exception) {
            finishSearch(generation);
            throw exception;
        }
    }

    /**
     * Runs on a pathetic worker thread — publishes the path and nothing else.
     */
    private void onPathResult(long generation, PathfinderResult result) {
        try {
            if (generation != searchGeneration.get() || currentGoal == null) {
                return;
            }
            activePath.set(NavigationPath.of(result.getPath()));
        } finally {
            finishSearch(generation);
        }
    }

    private void onPathFailure(long generation, PathfinderResult result) {
        finishSearch(generation);
        PathState state = result.getPathState();
        if (state == PathState.ABORTED || generation != searchGeneration.get()) {
            return;
        }
        reportFailure(generation, state, null);
    }

    private void onPathError(long generation, Throwable throwable) {
        finishSearch(generation);
        if (generation != searchGeneration.get()) {
            return;
        }
        reportFailure(generation, PathState.FAILED, throwable);
    }

    /**
     * Releases the search slot, but only for the search that still owns it. A callback arriving
     * late from a search that was already superseded must not clear the flag of the search that
     * replaced it, otherwise two searches would run at once.
     */
    private void finishSearch(long generation) {
        if (generation == searchGeneration.get()) {
            searchInProgress.set(false);
        }
    }

    /**
     * Hops back onto the main thread; a synchronous Bukkit event may not be fired from a worker.
     */
    private void reportFailure(long generation, PathState state, @Nullable Throwable throwable) {
        runOnMain(() -> {
            Player player = this.player;
            if (player == null || currentGoal == null || generation != searchGeneration.get()) {
                return;
            }
            new PathFindFailedEvent(player, this, state, throwable).callEvent();
            Consumer<Player> callback = pathFailCallback;
            if (callback != null) {
                callback.accept(player);
            }
        });
    }

    // ------------------------------------------------------------------ tick

    /**
     * Advances the navigation by one step. Called by {@link PlayerTickTask} on the main thread.
     */
    protected void tick() {
        Player player = this.player;
        if (player == null) {
            return;
        }

        Vector goal = currentGoal;
        if (goal == null) {
            hideBossBar(player);
            return;
        }

        if (!player.getWorld().getUID().equals(worldIdSnapshot)) {
            pauseNavigation(NavigationPausedEvent.Reason.WORLD_CHANGE);
            return;
        }

        Location location = player.getLocation();
        Vector here = location.toVector();
        double distance = here.distance(goal);
        double range = pauseBeyondRange;
        boolean outOfRange = range > 0 && distance > range;

        if (paused) {
            if (outOfRange) {
                return;
            }
            resumeNavigationInternal();
        } else if (outOfRange) {
            pauseNavigation(NavigationPausedEvent.Reason.OUT_OF_RANGE);
            return;
        }

        if (distance <= arrivalRadius) {
            new GoalReachedEvent(player, this, goal.clone()).callEvent();
            resetState();
            hideBossBar(player);
            return;
        }

        NavigationPath path = activePath.get();
        if (path != renderedPath) {
            renderedPath = path;
            lastPathIndex = 0;
        }
        if (path != null) {
            lastPathIndex = path.nearestIndex(here, lastPathIndex, NEAREST_INDEX_WINDOW);
        }

        if (isPathStale(path, here)) {
            requestPath(player, here);
        }

        PathParticleRenderer renderer = pathParticleRenderer;
        if (particle && renderer != null && path != null) {
            renderer.render(player, path, lastPathIndex);
        }

        BossBarNavigator navigator = bossBarNavigator;
        if (bossBar && navigator != null) {
            Vector target = path == null ? null : path.pointAhead(lastPathIndex, directionLookahead);
            Direction direction = Direction.of(location.getYaw(), here, target == null ? goal : target);
            float progress = (float) Math.clamp(1.0 - distance / initialDistance, 0.0, 1.0);
            navigator.update(player, goal.clone(), direction, progress);
        }
    }

    private boolean isPathStale(@Nullable NavigationPath path, Vector here) {
        if (path == null) {
            return System.currentTimeMillis() - lastComputeMs > recomputeCooldownMs;
        }
        return System.currentTimeMillis() - lastComputeMs > recomputeCooldownMs
                && path.distanceTo(here, lastPathIndex) > recomputeDeviation;
    }

    // --------------------------------------------------------------- lifecycle

    /**
     * Releases the player. Fires {@link GoalResetEvent} if a goal was still active.
     */
    public void close() {
        close(GoalResetEvent.Reason.DISCONNECT);
    }

    public void close(GoalResetEvent.@NotNull Reason reason) {
        runOnMain(() -> closeInternal(reason));
    }

    private synchronized void closeInternal(GoalResetEvent.Reason reason) {
        Player player = this.player;
        if (player == null) {
            return;
        }
        clearGoalInternal(reason);
        hideBossBar(player);
        this.player = null;
    }

    public boolean isClosed() {
        return player == null;
    }

    private void resetState() {
        abortSearch();
        currentGoal = null;
        worldIdSnapshot = null;
        paused = false;
        activePath.set(null);
        renderedPath = null;
        lastPathIndex = 0;
        initialDistance = 1.0;
        lastComputeMs = 0L;
    }

    /**
     * Invalidates any in-flight search. Bumping the generation makes a late callback a no-op even
     * if {@link PathfindingSearch#abort()} does not land in time.
     */
    private void abortSearch() {
        searchGeneration.incrementAndGet();
        PathfindingSearch search = currentSearch;
        currentSearch = null;
        if (search != null && !search.done()) {
            search.abort();
        }
        searchInProgress.set(false);
    }

    private void hideBossBar(Player player) {
        BossBarNavigator navigator = bossBarNavigator;
        if (navigator != null) {
            navigator.hide(player);
        }
    }

    private void runOnMain(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
        }
    }

    // ---------------------------------------------------------------- accessors

    /**
     * @return the player's current position, or {@code null} once this instance is closed
     */
    public @Nullable Vector getLocation() {
        Player player = this.player;
        return player == null ? null : player.getLocation().toVector();
    }

    public @Nullable Player getPlayer() {
        return player;
    }

    public boolean isParticle() {
        return particle;
    }

    public void setParticle(boolean particle) {
        this.particle = particle;
    }

    public boolean isBossBar() {
        return bossBar;
    }

    public void setBossBar(boolean bossBar) {
        this.bossBar = bossBar;
        if (!bossBar) {
            runOnMain(() -> {
                Player player = this.player;
                if (player != null) {
                    hideBossBar(player);
                }
            });
        }
    }

    public @Nullable PathParticleRenderer getPathParticleRenderer() {
        return pathParticleRenderer;
    }

    public void setPathParticleRenderer(@Nullable PathParticleRenderer pathParticleRenderer) {
        this.pathParticleRenderer = pathParticleRenderer;
    }

    public @Nullable BossBarNavigator getBossBarNavigator() {
        return bossBarNavigator;
    }

    public void setBossBarNavigator(@Nullable BossBarNavigator bossBarNavigator) {
        this.bossBarNavigator = bossBarNavigator;
    }

    public double getPauseBeyondRange() {
        return pauseBeyondRange;
    }

    /**
     * @param pauseBeyondRange distance to the goal beyond which rendering pauses;
     *                         0 or less disables pausing entirely
     */
    public void setPauseBeyondRange(double pauseBeyondRange) {
        this.pauseBeyondRange = pauseBeyondRange;
    }

    public long getRecomputeCooldownMs() {
        return recomputeCooldownMs;
    }

    public void setRecomputeCooldownMs(long recomputeCooldownMs) {
        this.recomputeCooldownMs = Math.max(0L, recomputeCooldownMs);
    }

    public double getRecomputeDeviation() {
        return recomputeDeviation;
    }

    public void setRecomputeDeviation(double recomputeDeviation) {
        this.recomputeDeviation = Math.max(0.0, recomputeDeviation);
    }

    public double getArrivalRadius() {
        return arrivalRadius;
    }

    public void setArrivalRadius(double arrivalRadius) {
        this.arrivalRadius = Math.max(0.0, arrivalRadius);
    }

    public double getDirectionLookahead() {
        return directionLookahead;
    }

    public void setDirectionLookahead(double directionLookahead) {
        this.directionLookahead = Math.max(0.0, directionLookahead);
    }

    public @Nullable Consumer<Player> getPathFailCallback() {
        return pathFailCallback;
    }

    /**
     * @param pathFailCallback run on the main thread whenever a search yields no path;
     *                         {@code null} disables the default action bar message
     */
    public void setPathFailCallback(@Nullable Consumer<Player> pathFailCallback) {
        this.pathFailCallback = pathFailCallback;
    }
}
