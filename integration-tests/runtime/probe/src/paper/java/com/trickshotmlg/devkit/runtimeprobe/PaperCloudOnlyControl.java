package com.trickshotmlg.devkit.runtimeprobe;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.Command;

/** Direct Cloud/Paper control command, intentionally bypassing MinecraftDevKit's registrar. */
final class PaperCloudOnlyControl {
    private PaperCloudOnlyControl() { }

    static void register(JavaPlugin plugin) {
        PaperCommandManager<CommandSourceStack> manager = PaperCommandManager.builder()
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(plugin);
        register(manager, plugin, "public", null);
        register(manager, plugin, "inspect", "devkitcloudonly.inspect");
        register(manager, plugin, "modify", "devkitcloudonly.modify");
        register(manager, plugin, "admin reload", "devkitcloudonly.admin.reload");
        register(manager, plugin, "admin reset", "devkitcloudonly.admin.reset");
        register(manager, plugin, "region info", "devkitcloudonly.region.info");
        register(manager, plugin, "region list", "devkitcloudonly.region.list");
        register(manager, plugin, "region add", "devkitcloudonly.region.add");
        register(manager, plugin, "region delete", "devkitcloudonly.region.delete");
        register(manager, plugin, "secret", "devkitcloudonly.secret");
        plugin.getLogger().info("MDK_CLOUD_ONLY_CONTROL_REGISTERED");
    }

    private static void register(PaperCommandManager<CommandSourceStack> manager, JavaPlugin plugin,
                                 String path, String permission) {
        Command.Builder<CommandSourceStack> builder = manager.commandBuilder("cloudonlyprobe");
        for (String literal : path.split(" ")) builder = builder.literal(literal);
        if (permission != null) builder = builder.permission(Permission.of(permission));
        manager.command(builder.handler(context -> plugin.getLogger().info("MDK_CLOUD_ONLY_HANDLER " + path)).build());
    }
}
