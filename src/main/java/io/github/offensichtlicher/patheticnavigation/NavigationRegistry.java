package io.github.offensichtlicher.patheticnavigation;

import io.github.offensichtlicher.patheticnavigation.event.GoalResetEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns every live {@link NavigatablePlayer} and is the single place they are created.
 * <p>
 * Backed by a {@link ConcurrentHashMap}, so lookups are safe from any thread.
 */
public final class NavigationRegistry {

    private final ConcurrentHashMap<UUID, NavigatablePlayer> instances = new ConcurrentHashMap<>();
    private final Supplier<NavigatablePlayerFactory> factorySupplier;

    /**
     * @param factorySupplier resolved per creation so a third party plugin can take over through
     *                        the services manager at any point
     */
    public NavigationRegistry(@NotNull Supplier<NavigatablePlayerFactory> factorySupplier) {
        this.factorySupplier = factorySupplier;
    }

    public @NotNull NavigatablePlayer getOrCreate(@NotNull Player player) {
        return instances.compute(player.getUniqueId(), (id, existing) ->
                existing == null || existing.isClosed()
                        ? factorySupplier.get().createNavigatablePlayer(player)
                        : existing);
    }

    public @Nullable NavigatablePlayer get(@NotNull UUID playerId) {
        return instances.get(playerId);
    }

    public @Nullable NavigatablePlayer remove(@NotNull UUID playerId) {
        return instances.remove(playerId);
    }

    /**
     * Runs {@code action} for every live instance and drops the closed ones on the way.
     */
    public void forEachAndPrune(@NotNull Consumer<NavigatablePlayer> action) {
        if (instances.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, NavigatablePlayer>> iterator = instances.entrySet().iterator();
        while (iterator.hasNext()) {
            NavigatablePlayer navigatablePlayer = iterator.next().getValue();
            if (navigatablePlayer.isClosed()) {
                iterator.remove();
                continue;
            }
            action.accept(navigatablePlayer);
        }
    }

    public void closeAll(GoalResetEvent.@NotNull Reason reason) {
        instances.values().forEach(navigatablePlayer -> navigatablePlayer.close(reason));
        instances.clear();
    }

    public int size() {
        return instances.size();
    }
}
