package com.trickshotmlg.devkit.bukkit.commands;

import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.commands.Arguments;
import org.bukkit.entity.Player;

/** Platform-specific typed command argument factories. */
public final class BukkitArguments {
    private BukkitArguments() { }

    /**
     * Creates a player-name argument resolved by Cloud's native Bukkit parser.
     * @param name typed argument handle name
     * @return player argument descriptor
     */
    public static Argument<Player> player(String name) {
        return Arguments.custom(name, Player.class);
    }
}
