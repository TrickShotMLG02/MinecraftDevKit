package com.trickshotmlg.devkit.examples.friendnet;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter;
import com.trickshotmlg.devkit.localization.ResourceBasedTextProvider;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/** Minimal JavaPlugin entry point for the standalone command example. */
public final class FriendNetPlugin extends JavaPlugin {
    @Override public void onEnable() {
        ResourceBasedTextProvider textProvider = ResourceBasedTextProvider.fromClasspath(
                getClass().getClassLoader(), "translations", List.of(Locale.US, Locale.GERMANY), Locale.US,
                MissingTranslationPolicy.SHOW_KEY,
                diagnostic -> getLogger().warning(diagnostic.toString()));
        ExampleMessages.provider(textProvider);
        new BukkitCommandAdapter(this, textProvider, Locale.US).register(FriendCommands.DEFINITION);
    }
}
