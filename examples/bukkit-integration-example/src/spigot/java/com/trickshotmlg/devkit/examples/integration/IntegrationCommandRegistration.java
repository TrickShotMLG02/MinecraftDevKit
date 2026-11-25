package com.trickshotmlg.devkit.examples.integration;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter;
import com.trickshotmlg.devkit.text.TextProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Explicit Spigot build selection for the common legacy Bukkit command backend. */
final class IntegrationCommandRegistration {
    private IntegrationCommandRegistration() { }

    static void register(JavaPlugin plugin, TextProvider text, com.trickshotmlg.devkit.commands.CommandDefinition definition) {
        new BukkitCommandAdapter(plugin, text, Locale.ENGLISH).register(definition);
    }
}
