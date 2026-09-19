package dev.sweeper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sweeper.SweeperPlugin;
import dev.sweeper.message.Messages;
import dev.sweeper.player.PlayerPrefs;
import dev.sweeper.sweep.Scope;
import dev.sweeper.sweep.SweepService;
import dev.sweeper.util.TimeFormat;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /sweeper (alias /sweep) with the player switches and the admin tools.
 */
public final class SweeperCommand {

    private final SweeperPlugin plugin;
    private final SweepService service;
    private final PlayerPrefs prefs;
    private final Messages messages;

    public SweeperCommand(SweeperPlugin plugin, SweepService service, PlayerPrefs prefs, Messages messages) {
        this.plugin = plugin;
        this.service = service;
        this.prefs = prefs;
        this.messages = messages;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("sweeper")
                .then(Commands.literal("timer")
                        .requires(source -> source.getSender().hasPermission(Perms.TIMER))
                        .executes(this::timer))
                .then(Commands.literal("messages")
                        .requires(source -> source.getSender().hasPermission(Perms.MESSAGES))
                        .executes(this::toggleMessages))
                .then(Commands.literal("protection")
                        .requires(source -> source.getSender().hasPermission(Perms.PROTECTION))
                        .executes(this::toggleProtection))
                .then(Commands.literal("clean")
                        .requires(source -> source.getSender().hasPermission(Perms.ADMIN))
                        .executes(ctx -> clean(ctx, Scope.ALL))
                        .then(Commands.literal("items").executes(ctx -> clean(ctx, Scope.ITEMS)))
                        .then(Commands.literal("entities").executes(ctx -> clean(ctx, Scope.ENTITIES))))
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission(Perms.ADMIN))
                        .executes(this::reload));
    }

    private int timer(CommandContext<CommandSourceStack> ctx) {
        messages.send(sender(ctx), "timer", Messages.text("time", TimeFormat.compact(service.secondsRemaining())));
        return Command.SINGLE_SUCCESS;
    }

    private int toggleMessages(CommandContext<CommandSourceStack> ctx) {
        if (sender(ctx) instanceof Player player) {
            final boolean on = prefs.toggleMessages(player);
            messages.send(player, "messages_toggle", messages.state(on));
        } else {
            messages.send(sender(ctx), "players_only");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int toggleProtection(CommandContext<CommandSourceStack> ctx) {
        if (sender(ctx) instanceof Player player) {
            final boolean on = prefs.toggleProtection(player);
            messages.send(player, "protection_toggle", messages.state(on));
        } else {
            messages.send(sender(ctx), "players_only");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int clean(CommandContext<CommandSourceStack> ctx, Scope scope) {
        final CommandSender sender = sender(ctx);
        messages.send(sender, service.sweepNow(scope) ? "clean_started" : "clean_busy");
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        plugin.reload();
        messages.send(sender(ctx), "reload");
        return Command.SINGLE_SUCCESS;
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }
}
