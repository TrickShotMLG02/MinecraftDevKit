package com.trickshotmlg.devkit.examples.lifecycle;

import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import net.kyori.adventure.text.Component;

final class LifecycleExampleCommands {
    private LifecycleExampleCommands() { }

    static CommandDefinition create(LifecycleExamplePlugin plugin) {
        return CommandRegistry.builder().root("mdklifecycle", root -> root
                .description("Demonstrate managed service lifecycle")
                .execute(context -> context.sender().sendMessage(Component.text("Use status, services, or reload.")))
                .subcommand("status", command -> command.execute(context -> plugin.showStatus(context.sender())))
                .subcommand("services", command -> command.execute(context -> plugin.showServices(context.sender())))
                .subcommand("reload", command -> command.execute(context -> plugin.reload(context.sender())))
        ).build();
    }
}
