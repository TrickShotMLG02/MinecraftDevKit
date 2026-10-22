package com.trickshotmlg.devkit.examples.config;

import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.commands.Arguments;
import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import net.kyori.adventure.text.Component;

final class ConfigExampleCommands {
    private static final Argument<Integer> MAXIMUM = Arguments.integer("value", 1, 1000);
    private ConfigExampleCommands() { }

    static CommandDefinition create(ConfigExamplePlugin plugin) {
        return CommandRegistry.builder().root("mdkconfig", root -> root
                .description("View and update the typed example configuration")
                .subcommand("show", command -> command.execute(context -> {
                    context.sender().sendMessage(Component.text("friends.max=" + plugin.config().store().get(FriendConfig.MAX_FRIENDS)
                            + ", requests.enabled=" + plugin.config().store().get(FriendConfig.REQUESTS_ENABLED)
                            + ", requests.timeout=" + plugin.config().store().get(FriendConfig.REQUEST_TIMEOUT)));
                }))
                .subcommand("setmax", command -> command.required(MAXIMUM).execute(context -> {
                    plugin.config().store().set(FriendConfig.MAX_FRIENDS, context.get(MAXIMUM));
                    context.sender().sendMessage(Component.text("Updated friends.max in memory; use /mdkconfig save to persist."));
                }))
                .subcommand("reload", command -> command.execute(context -> plugin.reloadConfiguration(context.sender())))
                .subcommand("save", command -> command.execute(context -> plugin.saveConfiguration(context.sender()))))
                .build();
    }
}
