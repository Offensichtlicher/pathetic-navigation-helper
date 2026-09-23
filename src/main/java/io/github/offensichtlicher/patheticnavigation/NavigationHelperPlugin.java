package io.github.offensichtlicher.patheticnavigation;

import de.bsommerfeld.pathetic.api.factory.PathfinderFactory;
import de.bsommerfeld.pathetic.api.pathing.Pathfinder;
import de.bsommerfeld.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.bsommerfeld.pathetic.bukkit.PatheticBukkit;
import de.bsommerfeld.pathetic.bukkit.provider.LoadingNavigationPointProvider;
import de.bsommerfeld.pathetic.engine.factory.AStarPathfinderFactory;
import io.github.offensichtlicher.patheticnavigation.bossbar.DefaultBossBarNavigator;
import io.github.offensichtlicher.patheticnavigation.command.GoalCommand;
import io.github.offensichtlicher.patheticnavigation.event.GoalResetEvent;
import io.github.offensichtlicher.patheticnavigation.particle.DefaultPathParticleRenderer;
import io.github.offensichtlicher.patheticnavigation.processor.HazardValidationProcessor;
import io.github.offensichtlicher.patheticnavigation.processor.MovementValidationProcessor;
import io.github.offensichtlicher.patheticnavigation.processor.SwimCostProcessor;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class NavigationHelperPlugin extends JavaPlugin {

    /** Rendering interval in ticks. Two ticks keep the particle line smooth without spamming packets. */
    private static final long TICK_PERIOD = 2L;
    /**
     * Upper bound for a single search. High enough for the default pause range of 512 blocks,
     * low enough to keep a worker thread from grinding for minutes on an unreachable goal.
     */
    private static final int MAX_ITERATIONS = 300_000;
    /** Body height the path has to fit through, matching a player. */
    private static final double PLAYER_HEIGHT = 1.8;

    private static volatile @Nullable NavigationHelperPlugin instance;

    private NavigationRegistry registry;
    private NavigatablePlayerFactory defaultFactory;
    private @Nullable ScheduledTask task;

    @Override
    public void onEnable() {
        instance = this;
        PatheticBukkit.initialize(this);

        PathfinderFactory pathfinderFactory = new AStarPathfinderFactory();
        PathfinderConfiguration configuration = PathfinderConfiguration.builder()
                .provider(new LoadingNavigationPointProvider())
                .validationProcessors(List.of(
                        new MovementValidationProcessor(PLAYER_HEIGHT, true),
                        new HazardValidationProcessor(PLAYER_HEIGHT)))
                // Swimming is allowed by the validator above, but penalised here so the path only
                // takes to the water when walking around would be a far worse deal.
                .costProcessor(List.of(new SwimCostProcessor()))
                .async(true)
                .maxIterations(MAX_ITERATIONS)
                .build();
        Pathfinder pathfinder = pathfinderFactory.createPathfinder(configuration);

        defaultFactory = new NavigatablePlayerFactory(this, pathfinder,
                new DefaultPathParticleRenderer(), new DefaultBossBarNavigator());
        getServer().getServicesManager()
                .register(NavigatablePlayerFactory.class, defaultFactory, this, ServicePriority.Lowest);

        registry = new NavigationRegistry(this::resolveFactory);
        getServer().getPluginManager().registerEvents(new IntegrityListener(registry), this);

        // Main thread: the boss bar is not thread-safe and synchronous events may not be fired
        // from a worker. Only the pathfinding itself runs off-thread, on pathetic's own pool.
        task = getServer().getGlobalRegionScheduler()
                .runAtFixedRate(this, new PlayerTickTask(registry, getLogger()), 1L, TICK_PERIOD);

        GoalCommand.register(this);
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (registry != null) {
            registry.closeAll(GoalResetEvent.Reason.SHUTDOWN);
        }
        PatheticBukkit.shutdown();
        instance = null;
    }

    /**
     * @return the navigation state of the given player, creating it on first use
     */
    public @NotNull NavigatablePlayer getPlayer(@NotNull Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player must not be null");
        }
        return registry.getOrCreate(player);
    }

    public @NotNull NavigationRegistry getRegistry() {
        return registry;
    }

    /**
     * Prefers a factory registered by another plugin, falling back to the built-in one.
     */
    private @NotNull NavigatablePlayerFactory resolveFactory() {
        RegisteredServiceProvider<NavigatablePlayerFactory> registration =
                getServer().getServicesManager().getRegistration(NavigatablePlayerFactory.class);
        return registration == null ? defaultFactory : registration.getProvider();
    }

    public static @NotNull NavigationHelperPlugin get() {
        NavigationHelperPlugin plugin = instance;
        if (plugin == null) {
            throw new IllegalStateException("NavigationHelper is not enabled");
        }
        return plugin;
    }
}
