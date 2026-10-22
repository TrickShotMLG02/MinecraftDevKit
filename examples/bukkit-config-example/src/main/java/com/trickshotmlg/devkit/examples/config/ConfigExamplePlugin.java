package com.trickshotmlg.devkit.examples.config;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter;
import com.trickshotmlg.devkit.config.ConfigChange;
import com.trickshotmlg.devkit.config.yaml.ConfigMigrationRegistry;
import com.trickshotmlg.devkit.config.yaml.UnknownKeyPolicy;
import com.trickshotmlg.devkit.config.yaml.YamlConfigHandle;
import com.trickshotmlg.devkit.config.yaml.YamlReloadPolicy;
import com.trickshotmlg.devkit.config.yaml.YamlReloadResult;
import com.trickshotmlg.devkit.config.yaml.YamlSaveOptions;
import com.trickshotmlg.devkit.config.yaml.YamlSaveResult;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small Paper plugin demonstrating the public typed configuration API. */
public final class ConfigExamplePlugin extends JavaPlugin {
    private YamlConfigHandle config;
    private ExecutorService ioExecutor;
    private AutoCloseable changes;

    @Override public void onEnable() {
        Path path = getDataFolder().toPath().resolve("config.yml");
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) saveResource("config.yml", false);
            ConfigMigrationRegistry migrations = ConfigMigrationRegistry.builder()
                    .register(1, 2, values -> values.move("friends.max-count", "friends.max"))
                    .build();
            config = YamlConfigHandle.load(path, FriendConfig.SCHEMA, UnknownKeyPolicy.PRESERVE_AND_WARN, migrations);
            ioExecutor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "mdk-config-io"); thread.setDaemon(true); return thread;
            });
            changes = config.store().onChange(FriendConfig.MAX_FRIENDS, change -> getLogger().info(
                    "friends.max changed from " + change.oldValue() + " to " + change.newValue() + " (" + change.origin() + ")"));
            new BukkitCommandAdapter(this, new StaticTextProvider(), java.util.Locale.ENGLISH)
                    .register(ConfigExampleCommands.create(this));
        } catch (IOException | RuntimeException exception) {
            getLogger().severe("Configuration initialization failed: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    YamlConfigHandle config() { return config; }

    void reloadConfiguration(com.trickshotmlg.devkit.commands.CommandSender sender) {
        config.reloadAsync(YamlReloadPolicy.REJECT_IF_DIRTY, ioExecutor).whenComplete((result, failure) ->
                getServer().getScheduler().runTask(this, () -> {
                    if (failure != null) sender.sendMessage(Component.text("Reload failed: " + failure.getMessage()));
                    else sender.sendMessage(Component.text(reloadMessage(result)));
                }));
    }

    void saveConfiguration(com.trickshotmlg.devkit.commands.CommandSender sender) {
        ioExecutor.execute(() -> {
            YamlSaveResult result = config.save(YamlSaveOptions.safeRewrite());
            getServer().getScheduler().runTask(this, () -> sender.sendMessage(Component.text(
                    result.success() ? "Configuration saved." : "Save failed: " + result.error().orElse("unknown error"))));
        });
    }

    private String reloadMessage(YamlReloadResult result) {
        if (result.conflict()) return "Reload rejected because there are unsaved changes. Save or discard them first.";
        return result.success() ? "Configuration reloaded at generation " + result.generation() + "."
                : "Reload failed; previous values remain active: " + result.error().orElse("unknown error");
    }

    @Override public void onDisable() {
        if (changes != null) try { changes.close(); } catch (Exception ignored) { }
        if (config != null) config.close();
        if (ioExecutor != null) ioExecutor.shutdownNow();
    }
}
