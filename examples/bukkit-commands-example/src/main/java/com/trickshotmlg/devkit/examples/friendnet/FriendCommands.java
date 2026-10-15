package com.trickshotmlg.devkit.examples.friendnet;

import com.trickshotmlg.devkit.bukkit.commands.BukkitArguments;
import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.commands.Arguments;
import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import com.trickshotmlg.devkit.text.TextRef;
import org.bukkit.entity.Player;

/** Demonstrates nested Cloud commands, typed Bukkit arguments, permissions, and localized metadata. */
public final class FriendCommands {
    public static final Argument<Integer> PAGE = Arguments.integer("page", 1, 250)
            .withDescription(TextRef.translated("arguments.page.description"));
    public static final Argument<Player> TARGET = BukkitArguments.player("player")
            .withDescription(TextRef.translated("arguments.player.description"));
    public static final CommandDefinition DEFINITION = create();
    private FriendCommands() { }

    public static CommandDefinition create() {
        return CommandRegistry.builder()
                .root("friends", root -> root.aliases("friend", "f")
                        .description(TextRef.translated("commands.friends.description", "Manage your friends"))
                        .subcommand("list", list -> list
                                .permission(FriendPermissions.FRIEND_LIST).playerOnly()
                                .description(TextRef.translated("commands.friends.list.description"))
                                .optional(PAGE, 1)
                                .execute(context -> ExampleMessages.send(context, "friends.list", "Page {page}",
                                        "page", context.get(PAGE))))
                        .subcommand("add", add -> add
                                .permission(FriendPermissions.FRIEND_ADD).playerOnly()
                                .description(TextRef.translated("commands.friends.add.description"))
                                .usage(TextRef.translated("commands.friends.add.usage"))
                                .required(TARGET)
                                .execute(context -> ExampleMessages.send(context, "friends.add.success",
                                        "Added {player} as a friend.", "player", context.get(TARGET).getName())))
                        .subcommand("remove", remove -> remove
                                .permission(FriendPermissions.FRIEND_REMOVE).playerOnly()
                                .description(TextRef.translated("commands.friends.remove.description"))
                                .required(TARGET)
                                .execute(context -> ExampleMessages.send(context, "friends.remove.success",
                                        "Removed {player} as a friend.", "player", context.get(TARGET).getName())))
                        .subcommand("requests", requests -> requests
                                .description(TextRef.translated("commands.friends.requests.description"))
                                .execute(context -> ExampleMessages.literal(context,
                                        "Request overview is an example response."))
                                .subcommand("list", list -> list
                                        .permission(FriendPermissions.REQUEST_LIST).playerOnly()
                                        .description(TextRef.translated("commands.friends.requests.list.description"))
                                        .execute(context -> ExampleMessages.send(context, "requests.list",
                                                "You have no pending requests.")))
                                .subcommand("accept", accept -> accept
                                        .permission(FriendPermissions.REQUEST_ACCEPT).playerOnly()
                                        .description(TextRef.translated("commands.friends.requests.accept.description"))
                                        .required(TARGET)
                                        .execute(context -> ExampleMessages.send(context, "requests.accept.success",
                                                "Accepted {player}'s request.", "player", context.get(TARGET).getName()))
                                        .subcommand("all", all -> all
                                                .permission(FriendPermissions.REQUEST_ACCEPT_ALL).playerOnly()
                                                .description(TextRef.translated("commands.friends.requests.accept.all.description"))
                                                .execute(context -> ExampleMessages.send(context, "requests.accept.all.success",
                                                        "Accepted all pending requests."))))
                                .subcommand("deny", deny -> deny
                                        .permission(FriendPermissions.REQUEST_DENY).playerOnly()
                                        .description(TextRef.translated("commands.friends.requests.deny.description"))
                                        .required(TARGET)
                                        .execute(context -> ExampleMessages.send(context, "requests.deny.success",
                                                "Denied {player}'s request.", "player", context.get(TARGET).getName()))))
                        .subcommand("admin", admin -> admin.permission(FriendPermissions.ADMIN_COMMAND).playerOnly()
                                .description(TextRef.translated("commands.friends.admin.description"))
                                .subcommand("reload", reload -> reload.permission(FriendPermissions.RELOAD)
                                        .description(TextRef.translated("commands.friends.admin.reload.description"))
                                        .execute(context -> ExampleMessages.send(context, "admin.reload.success",
                                                "FriendNet example data reloaded.")))))
                .build();
    }
}
