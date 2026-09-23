package io.github.offensichtlicher.patheticnavigation.bossbar;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Renders {@code Go to <x>, <y>, <z> [<arrow>]} with a progress bar that fills as the player
 * closes in on the goal.
 * <p>
 * Holds one boss bar instance, which is why the factory hands every player their own copy.
 * Only touched from the main thread.
 */
public class DefaultBossBarNavigator implements BossBarNavigator {

    private BossBar.Color color = BossBar.Color.GREEN;
    private BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;

    private BossBar bossBar;
    private boolean shown;

    @Override
    public void update(@NotNull Player player, @NotNull Vector goal, @NotNull Direction direction, float progress) {
        Component name = buildName(goal, direction);
        float clamped = (float) Math.clamp(progress, BossBar.MIN_PROGRESS, BossBar.MAX_PROGRESS);

        if (bossBar == null) {
            bossBar = BossBar.bossBar(name, clamped, color, overlay);
        } else {
            // Only write what actually changed; every setter notifies Paper's listener, which
            // then sends a packet.
            if (!name.equals(bossBar.name())) {
                bossBar.name(name);
            }
            if (bossBar.progress() != clamped) {
                bossBar.progress(clamped);
            }
            if (bossBar.color() != color) {
                bossBar.color(color);
            }
            if (bossBar.overlay() != overlay) {
                bossBar.overlay(overlay);
            }
        }

        if (!shown) {
            player.showBossBar(bossBar);
            shown = true;
        }
    }

    @Override
    public void hide(@NotNull Player player) {
        if (!shown || bossBar == null) {
            return;
        }
        player.hideBossBar(bossBar);
        shown = false;
    }

    protected @NotNull Component buildName(@NotNull Vector goal, @NotNull Direction direction) {
        return Component.text("Go to ", NamedTextColor.GRAY)
                .append(Component.text(format(goal), NamedTextColor.WHITE))
                .append(Component.text(" [", NamedTextColor.DARK_GRAY))
                .append(Component.text(direction.arrow(), NamedTextColor.AQUA))
                .append(Component.text("]", NamedTextColor.DARK_GRAY));
    }

    private static String format(Vector goal) {
        return goal.getBlockX() + ", " + goal.getBlockY() + ", " + goal.getBlockZ();
    }

    @Override
    public @NotNull BossBarNavigator copy() {
        DefaultBossBarNavigator copy = new DefaultBossBarNavigator();
        copy.color = color;
        copy.overlay = overlay;
        return copy;
    }

    public @NotNull BossBar.Color getColor() {
        return color;
    }

    public void setColor(@NotNull BossBar.Color color) {
        this.color = color;
    }

    public @NotNull BossBar.Overlay getOverlay() {
        return overlay;
    }

    public void setOverlay(@NotNull BossBar.Overlay overlay) {
        this.overlay = overlay;
    }
}
