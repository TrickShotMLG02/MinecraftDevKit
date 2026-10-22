package com.trickshotmlg.devkit.examples.permissions;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal server-side harness for inspecting Bukkit's effective permission checks. */
public final class FriendNetPlugin extends JavaPlugin implements CommandExecutor {
    private static final Map<String, String> DEMO_CHECKS = new LinkedHashMap<>();

    static {
        DEMO_CHECKS.put("default group node", FriendPermissions.DEFAULT.node());
        DEMO_CHECKS.put("extended group node", FriendPermissions.DEFAULT_EXTENDED.node());
        DEMO_CHECKS.put("moderator group node", FriendPermissions.MODERATOR.node());
        DEMO_CHECKS.put("admin group node", FriendPermissions.ADMIN.node());
        DEMO_CHECKS.put("list friends", FriendPermissions.FRIEND_LIST.node());
        DEMO_CHECKS.put("list other players' friends", FriendPermissions.FRIEND_LIST_OTHERS.node());
        DEMO_CHECKS.put("send friend requests", FriendPermissions.FRIEND_ADD.node());
        DEMO_CHECKS.put("accept requests", FriendPermissions.REQUEST_ACCEPT.node());
        DEMO_CHECKS.put("accept all requests", FriendPermissions.REQUEST_ACCEPT_ALL.node());
        DEMO_CHECKS.put("block players", FriendPermissions.FRIEND_BLOCK.node());
        DEMO_CHECKS.put("reload FriendNet", FriendPermissions.RELOAD.node());
        DEMO_CHECKS.put("proxy handshake", FriendPermissions.PROXY_HANDSHAKE.node());
    }

    @Override
    public void onEnable() {
        if (getCommand("mdkperms") == null) {
            throw new IllegalStateException("The mdkperms command is missing from plugin.yml");
        }
        getCommand("mdkperms").setExecutor(this);
        getLogger().info("Loaded " + FriendPermissions.CATALOG.permissions().size()
                + " permission nodes and " + FriendPermissions.CATALOG.groups().size() + " groups.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Run this command in game as a player.");
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            showCheck(player, args[1], "requested node");
            return true;
        }
        if (args.length != 0) {
            player.sendMessage("Usage: /mdkperms [check <permission.node>]");
            return true;
        }

        player.sendMessage("MinecraftDevKit / Bukkit permission checks for " + player.getName());
        DEMO_CHECKS.forEach((labelText, node) -> showCheck(player, node, labelText));
        player.sendMessage("Use /mdkperms check <node> to test an explicit or external assignment.");
        return true;
    }

    private void showCheck(Player player, String node, String description) {
        boolean granted = player.hasPermission(node);
        player.sendMessage((granted ? "TRUE  " : "FALSE ") + description + " (" + node + ")");
    }
}
