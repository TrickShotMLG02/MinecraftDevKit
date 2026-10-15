package com.trickshotmlg.devkit.examples.friendnet;

import com.trickshotmlg.devkit.commands.CommandContext;
import com.trickshotmlg.devkit.localization.LocalizationService;
import com.trickshotmlg.devkit.localization.ReloadResult;
import com.trickshotmlg.devkit.text.TextRef;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Runtime localized response helper; the service resolves each message for the sender's locale. */
final class ExampleMessages {
    private static final AtomicReference<LocalizationService> PROVIDER = new AtomicReference<>();
    private static final AtomicReference<JavaPlugin> PLUGIN = new AtomicReference<>();
    private static final AtomicReference<BukkitScheduler> SCHEDULER = new AtomicReference<>();
    private ExampleMessages() { }

    static void provider(JavaPlugin plugin, LocalizationService provider, BukkitScheduler scheduler) {
        PLUGIN.set(plugin);
        PROVIDER.set(provider);
        SCHEDULER.set(scheduler);
    }

    static void clear(LocalizationService provider) {
        if (provider != null) PROVIDER.compareAndSet(provider, null);
        PLUGIN.set(null);
        SCHEDULER.set(null);
    }

    static void send(CommandContext context, String key, String fallback, Object... pairs) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) values.put((String) pairs[index], pairs[index + 1]);
        LocalizationService provider = requireProvider();
        context.sender().sendMessage(provider.resolve(TextRef.translated(key, fallback), context.sender().locale(),
                values, Optional.of("FriendCommands." + key)));
    }

    static void literal(CommandContext context, String message) {
        context.sender().sendMessage(requireProvider().resolve(TextRef.literal(message), context.sender().locale(), Map.of()));
    }

    static void reload(CommandContext context) {
        LocalizationService provider = requireProvider();
        JavaPlugin plugin = java.util.Objects.requireNonNull(PLUGIN.get(), "plugin");
        BukkitScheduler scheduler = java.util.Objects.requireNonNull(SCHEDULER.get(), "scheduler");
        provider.reloadAsync().whenComplete((result, failure) -> {
            try {
                scheduler.runTask(plugin, () -> {
            if (failure != null) {
                sendFailure(context, provider, failure.getMessage() == null
                        ? failure.getClass().getSimpleName() : failure.getMessage());
            } else if (result.successful()) {
                context.sender().sendMessage(provider.resolve(TextRef.translated("localization.reload.success",
                        "Reloaded {count} translations across {locales} locales."), context.sender().locale(),
                        Map.of("count", result.translationCount(), "locales", result.locales().size())));
            } else {
                sendFailure(context, provider, result.error().orElse("unknown error"));
            }
                });
            } catch (IllegalPluginAccessException ignored) {
                // The plugin may have been disabled while the asynchronous reload was running.
            }
        });
    }

    private static void sendFailure(CommandContext context, LocalizationService provider, String error) {
        context.sender().sendMessage(provider.resolve(TextRef.translated("localization.reload.failure",
                "Localization reload failed: {error}"), context.sender().locale(), Map.of("error", error)));
    }

    private static LocalizationService requireProvider() {
        return java.util.Objects.requireNonNull(PROVIDER.get(), "text provider");
    }
}
