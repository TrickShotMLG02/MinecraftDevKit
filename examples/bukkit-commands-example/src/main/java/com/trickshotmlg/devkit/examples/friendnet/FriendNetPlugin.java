package com.trickshotmlg.devkit.examples.friendnet;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter;
import com.trickshotmlg.devkit.localization.LocalizationService;
import com.trickshotmlg.devkit.localization.ReloadResult;
import com.trickshotmlg.devkit.localization.yaml.YamlTranslationSource;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Minimal JavaPlugin entry point demonstrating reloadable external YAML translations. */
public final class FriendNetPlugin extends JavaPlugin {
    private LocalizationService localization;

    @Override public void onEnable() {
        Path languageDirectory = getDataFolder().toPath().resolve("lang");
        try {
            Files.createDirectories(languageDirectory);
            for (String file : new String[]{"en.yml", "en-GB.yml", "de.yml", "de-AT.yml"}) {
                saveResource("lang/" + file, false);
            }
            localization = LocalizationService.builder()
                    .source(YamlTranslationSource.bundled("lang", "en", "en-GB", "de", "de-AT"))
                    .source(YamlTranslationSource.directory(languageDirectory))
                    .defaultFallbackLocale(Locale.ENGLISH)
                    .defaultFallbackEnabled(true)
                    .missingKeyPolicy(MissingTranslationPolicy.SHOW_MARKER)
                    .diagnosticSink(diagnostic -> getLogger().warning(diagnostic.toString()))
                    .watcherFailureHandler(failure -> getLogger().warning(
                            "Localization watcher/reload error: " + failure.getMessage()))
                    .build();
            ReloadResult loaded = localization.reload();
            if (!loaded.successful()) throw new IllegalStateException(loaded.error().orElse("Localization load failed"));
            localization.startWatching();
        } catch (IOException | RuntimeException exception) {
            if (localization != null) localization.close();
            getLogger().severe("Could not initialize localization: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ExampleMessages.provider(this, localization, Bukkit.getScheduler());
        new BukkitCommandAdapter(this, localization, Locale.ENGLISH).register(FriendCommands.DEFINITION);
    }

    @Override public void onDisable() {
        ExampleMessages.clear(localization);
        if (localization != null) localization.close();
    }
}
