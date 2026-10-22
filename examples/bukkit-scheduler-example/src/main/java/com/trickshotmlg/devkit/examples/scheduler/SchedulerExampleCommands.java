package com.trickshotmlg.devkit.examples.scheduler;

import com.trickshotmlg.devkit.bukkit.commands.BukkitArguments;
import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import org.bukkit.entity.Player;

final class SchedulerExampleCommands {
    private static final Argument<Player> TARGET = BukkitArguments.player("player");

    private SchedulerExampleCommands() { }

    static CommandDefinition create(SchedulerExamplePlugin plugin) {
        return CommandRegistry.builder().root("mdkscheduler", root -> root
                .aliases("mdks")
                .description("Demonstrate MinecraftDevKit's task scheduler")
                .execute(context -> context.sender().sendMessage(net.kyori.adventure.text.Component.text(
                        "Use demo, repeat, cancel, async with a player name, or status.")))
                .subcommand("demo", command -> command.required(TARGET).execute(context ->
                        plugin.demo(context.get(TARGET))))
                .subcommand("repeat", command -> command.required(TARGET).execute(context ->
                        plugin.startRepeating(context.get(TARGET))))
                .subcommand("cancel", command -> command.required(TARGET).execute(context -> {
                    plugin.stopRepeating(context.get(TARGET));
                    context.sender().sendMessage(net.kyori.adventure.text.Component.text("Repeating task cancelled if one was active."));
                }))
                .subcommand("async", command -> command.required(TARGET).execute(context ->
                        plugin.runAsyncDemo(context.get(TARGET))))
                .subcommand("status", command -> command.execute(context -> plugin.sendStatus(context.sender())))
        ).build();
    }
}
