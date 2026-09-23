package io.github.offensichtlicher.patheticnavigation;

import de.bsommerfeld.pathetic.api.pathing.Pathfinder;
import io.github.offensichtlicher.patheticnavigation.bossbar.BossBarNavigator;
import io.github.offensichtlicher.patheticnavigation.particle.PathParticleRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Builds {@link NavigatablePlayer} instances with the plugin-wide defaults.
 * <p>
 * Register your own implementation with the services manager to take over creation:
 * {@code getServer().getServicesManager().register(NavigatablePlayerFactory.class, yours, plugin,
 * ServicePriority.High)}.
 */
public class NavigatablePlayerFactory {

    private final Plugin plugin;
    private final Pathfinder pathfinder;

    private @Nullable PathParticleRenderer pathParticleRenderer;
    private @Nullable BossBarNavigator bossBarNavigator;
    private boolean particleEnabled = true;
    private boolean bossBarEnabled = true;

    public NavigatablePlayerFactory(@NotNull Plugin plugin, @NotNull Pathfinder pathfinder,
                                    @Nullable PathParticleRenderer pathParticleRenderer,
                                    @Nullable BossBarNavigator bossBarNavigator) {
        this.plugin = plugin;
        this.pathfinder = pathfinder;
        this.pathParticleRenderer = pathParticleRenderer;
        this.bossBarNavigator = bossBarNavigator;
    }

    /**
     * Renderer and navigator are copied per player so their settings — particle type, line length,
     * bar colour — can be tuned individually without affecting anyone else.
     */
    public @NotNull NavigatablePlayer createNavigatablePlayer(@NotNull Player player) {
        NavigatablePlayer navigatablePlayer = new NavigatablePlayer(plugin, player, pathfinder);
        navigatablePlayer.setPathParticleRenderer(pathParticleRenderer == null ? null : pathParticleRenderer.copy());
        navigatablePlayer.setBossBarNavigator(bossBarNavigator == null ? null : bossBarNavigator.copy());
        navigatablePlayer.setParticle(particleEnabled);
        navigatablePlayer.setBossBar(bossBarEnabled);
        return navigatablePlayer;
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

    public boolean isParticleEnabled() {
        return particleEnabled;
    }

    public void setParticleEnabled(boolean particleEnabled) {
        this.particleEnabled = particleEnabled;
    }

    public boolean isBossBarEnabled() {
        return bossBarEnabled;
    }

    public void setBossBarEnabled(boolean bossBarEnabled) {
        this.bossBarEnabled = bossBarEnabled;
    }
}
