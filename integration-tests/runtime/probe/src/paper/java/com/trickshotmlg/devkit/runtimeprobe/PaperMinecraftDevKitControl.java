package com.trickshotmlg.devkit.runtimeprobe;

import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.paper.commands.PaperCommandAdapter;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Registers the same test definition through MinecraftDevKit's native Paper backend. */
final class PaperMinecraftDevKitControl {
    private PaperMinecraftDevKitControl() { }

    static void register(JavaPlugin plugin, CommandDefinition definition) {
        new PaperCommandAdapter(plugin, new StaticTextProvider(), Locale.ENGLISH).register(definition);
        plugin.getLogger().info("MDK_PAPER_NATIVE_CONTROL_REGISTERED");
    }
}
