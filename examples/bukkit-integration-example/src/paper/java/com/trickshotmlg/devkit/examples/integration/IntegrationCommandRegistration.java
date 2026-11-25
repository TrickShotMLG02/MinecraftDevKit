package com.trickshotmlg.devkit.examples.integration;

import com.trickshotmlg.devkit.paper.commands.PaperCommandAdapter;
import com.trickshotmlg.devkit.text.TextProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Explicit Paper build selection for Cloud's native Brigadier-backed command lifecycle. */
final class IntegrationCommandRegistration {
    private IntegrationCommandRegistration() { }

    static void register(JavaPlugin plugin, TextProvider text, com.trickshotmlg.devkit.commands.CommandDefinition definition) {
        new PaperCommandAdapter(plugin, text, Locale.ENGLISH).register(definition);
    }
}
