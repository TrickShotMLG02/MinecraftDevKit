package com.trickshotmlg.devkit.bukkit.commands;

import com.trickshotmlg.devkit.commands.CommandSender;

/** Bukkit-specific extension exposed by this adapter for handlers that need platform APIs. */
public interface BukkitCommandSender extends CommandSender {
    /** Returns the original Bukkit sender for platform integrations. */
    org.bukkit.command.CommandSender bukkitSender();
}
