package com.trickshotmlg.devkit.examples.storage;

import com.trickshotmlg.devkit.bukkit.commands.BukkitArguments;
import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandSender;
import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.commands.Arguments;
import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
final class StorageCommands {
    private static final Argument<Player> TARGET = BukkitArguments.player("player");
    private static final Argument<Long> AMOUNT = Arguments.longInteger("amount", 0L, Long.MAX_VALUE);
    private StorageCommands() { }

    static CommandDefinition create(StorageExamplePlugin plugin) {
        return CommandRegistry.builder().root("mdkstorage", root -> root
                .description("Demonstrate asynchronous MinecraftDevKit database storage")
                .execute(context -> context.sender().sendMessage(Component.text("Use balance, add, set, or status.")))
                .subcommand("balance", node -> node.permission(StoragePermissions.BALANCE).playerOnly().execute(context -> {
                    if (!(context.sender() instanceof BukkitCommandSender sender)
                            || !(sender.bukkitSender() instanceof Player player)) return;
                    UUID id = player.getUniqueId();
                    var lifecycle = plugin.lifecycle();
                    var scope = lifecycle.get(StorageExamplePlugin.TASKS);
                    lifecycle.get(StorageExamplePlugin.PLAYERS).balance(id).result().whenComplete((balance, error) ->
                            onServerThread(plugin, scope, id, () -> {
                                if (error != null) playerMessage(plugin, id, "Database read failed: " + error.getMessage());
                                else playerMessage(plugin, id, "Stored coins: " + balance.orElse(0L));
                            }));
                    StorageMessages.send(player, Component.text("Loading balance asynchronously..."));
                }))
                .subcommand("add", node -> node.permission(StoragePermissions.ADMIN).required(TARGET).required(AMOUNT)
                        .execute(context -> {
                            Player target = context.get(TARGET);
                            long amount = context.get(AMOUNT);
                            var scope = plugin.lifecycle().get(StorageExamplePlugin.TASKS);
                            plugin.lifecycle().get(StorageExamplePlugin.PLAYERS).add(target.getUniqueId(), amount)
                                    .result().whenComplete((changed, error) -> onServerThread(plugin, scope, target.getUniqueId(), () ->
                                            playerMessage(plugin, target.getUniqueId(), error == null
                                                    ? "Added " + amount + " coins." : "Database update failed.")));
                        }))
                .subcommand("set", node -> node.permission(StoragePermissions.ADMIN).required(TARGET).required(AMOUNT)
                        .execute(context -> {
                            Player target = context.get(TARGET);
                            long amount = context.get(AMOUNT);
                            var scope = plugin.lifecycle().get(StorageExamplePlugin.TASKS);
                            plugin.lifecycle().get(StorageExamplePlugin.PLAYERS).set(target.getUniqueId(), amount)
                                    .result().whenComplete((changed, error) -> onServerThread(plugin, scope, target.getUniqueId(), () ->
                                            playerMessage(plugin, target.getUniqueId(), error == null
                                                    ? "Set coins to " + amount + "." : "Database update failed.")));
                        }))
                .subcommand("status", node -> node.permission(StoragePermissions.ADMIN).execute(context -> {
                    var storage = plugin.lifecycle().get(StorageExamplePlugin.STORAGE);
                    context.sender().sendMessage(Component.text("Storage open=" + !storage.isClosed()
                            + ", backend=" + (storage.configuration().sqlite() ? "sqlite" : "network")
                            + ", lifecycle=" + plugin.lifecycle().state()));
                }))
        ).build();
    }

    private static void onServerThread(StorageExamplePlugin plugin, com.trickshotmlg.devkit.scheduler.TaskScope scope,
                                       UUID player, Runnable action) {
        if (scope.isClosed()) return;
        try { scope.sync().run(() -> { if (Bukkit.getPlayer(player) != null) action.run(); }); }
        catch (IllegalStateException ignored) { /* lifecycle shutdown won the race */ }
    }

    private static void playerMessage(StorageExamplePlugin plugin, UUID playerId, String message) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) StorageMessages.send(online, Component.text(message));
    }
}
