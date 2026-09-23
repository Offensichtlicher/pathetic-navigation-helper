package io.github.offensichtlicher.patheticnavigation;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;


class PlayerTickTask implements Consumer<ScheduledTask> {

    private final NavigationRegistry registry;
    private final Logger logger;

    PlayerTickTask(NavigationRegistry registry, Logger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    @Override
    public void accept(ScheduledTask scheduledTask) {
        registry.forEachAndPrune(navigatablePlayer -> {
            try {
                navigatablePlayer.tick();
            } catch (Exception exception) {
                // One broken navigation must not take the whole ticker down.
                logger.log(Level.SEVERE, "Failed to tick navigation for "
                        + navigatablePlayer.getPlayer(), exception);
            }
        });
    }
}
