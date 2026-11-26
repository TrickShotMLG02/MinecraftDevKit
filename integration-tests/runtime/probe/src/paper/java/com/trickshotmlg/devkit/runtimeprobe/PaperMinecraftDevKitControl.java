package com.trickshotmlg.devkit.runtimeprobe;

import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.paper.commands.PaperCommandAdapter;
import com.trickshotmlg.devkit.paper.commands.PaperPermissionSuggestionFilter;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Registers the same test definition through MinecraftDevKit's native Paper backend. */
final class PaperMinecraftDevKitControl {
    private PaperMinecraftDevKitControl() { }

    static void register(JavaPlugin plugin, CommandDefinition definition) {
        PaperCommandAdapter adapter = new PaperCommandAdapter(plugin, new StaticTextProvider(), Locale.ENGLISH);
        if (Boolean.getBoolean("mdk.protocol.permissionAwareCompletions")) {
            adapter.enablePermissionAwareCompletions();
        }
        adapter.register(definition);
        plugin.getLogger().info("MDK_PAPER_NATIVE_CONTROL_REGISTERED");
        if (Boolean.getBoolean("mdk.protocol.permissionAwareCompletions")) {
            plugin.getLogger().info("MDK_PAPER_PERMISSION_SUGGESTION_FILTER_ENABLED");
        }
    }

    static void enableFor(JavaPlugin plugin, CommandDefinition definition) {
        PaperPermissionSuggestionFilter filter = new PaperPermissionSuggestionFilter(plugin, Locale.ENGLISH);
        filter.register(definition);
        plugin.getLogger().info("MDK_PAPER_PERMISSION_SUGGESTION_FILTER_ENABLED");
    }
}
