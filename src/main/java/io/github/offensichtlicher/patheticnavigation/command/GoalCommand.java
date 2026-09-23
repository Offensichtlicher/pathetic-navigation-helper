package io.github.offensichtlicher.patheticnavigation.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.offensichtlicher.patheticnavigation.NavigatablePlayer;
import io.github.offensichtlicher.patheticnavigation.event.GoalResetEvent;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.math.FinePosition;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /goal <x> <y> <z>} and {@code /goal reset}.
 */
public final class GoalCommand {

    public static final String PERMISSION = "navigation.command.goal";

    private GoalCommand() {
    }

    public static void register(@NotNull JavaPlugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(build(), "Set or clear your navigation goal"));
    }

    private static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("goal")
                // Also hides the command from tab completion for players without the permission.
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("reset")
                        .executes(context -> {
                            Player player = context.getSource().getPlayerOrThrow();
                            NavigatablePlayer navigatablePlayer = NavigatablePlayer.of(player);
                            if (navigatablePlayer.getGoal() == null) {
                                player.sendMessage(Component.text("You have no active goal.", NamedTextColor.RED));
                                return 0;
                            }
                            navigatablePlayer.clearGoal(GoalResetEvent.Reason.COMMAND);
                            player.sendMessage(Component.text("Navigation goal cleared.", NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }))
                // finePosition is a single vec3 node consuming "x y z", with ~ and ^ support.
                .then(Commands.argument("position", ArgumentTypes.finePosition(true))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            Player player = source.getPlayerOrThrow();

                            FinePosition position = context
                                    .getArgument("position", FinePositionResolver.class)
                                    .resolve(source);
                            Vector goal = position.toVector();

                            NavigatablePlayer.of(player).setGoal(goal);
                            player.sendMessage(Component.text("Navigating to ", NamedTextColor.GREEN)
                                    .append(Component.text(goal.getBlockX() + ", " + goal.getBlockY()
                                            + ", " + goal.getBlockZ(), NamedTextColor.WHITE)));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
