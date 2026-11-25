package com.trickshotmlg.devkit.examples.integration;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandSender;
import com.trickshotmlg.devkit.commands.CommandContext;
import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import com.trickshotmlg.devkit.commands.Arguments;
import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.localization.LocalizationService;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Cloud command tree for the complete integration example. */
final class IntegrationCommands {
    private static final Argument<Integer> NATIVE_VALUE = Arguments.integer("value", 0, 100);
    private final JavaPlugin plugin;
    private final LifecycleManager lifecycle;
    private final LocalizationService text;

    IntegrationCommands(JavaPlugin plugin, LifecycleManager lifecycle, LocalizationService text) {
        this.plugin = plugin; this.lifecycle = lifecycle; this.text = text;
    }

    CommandDefinition definition() {
        return CommandRegistry.builder().root("mdkintegration", root -> root
                .aliases("mdki")
                .description(TextRef.translated("integration.menu.title", "MinecraftDevKit Integration"))
                .execute(context -> open(context))
                .subcommand("menu", menu -> menu.permission(IntegrationPermissions.USE).playerOnly()
                        .description(TextRef.translated("integration.menu.players", "Open the integration menus"))
                        .execute(this::open))
                .subcommand("status", status -> status
                        .description(TextRef.translated("integration.status.title", "Show application status"))
                        .execute(this::status))
                .subcommand("save", save -> save.permission(IntegrationPermissions.ADMIN)
                        .description(TextRef.translated("integration.settings.save", "Save configuration"))
                        .execute(this::save))
                .subcommand("reload", reload -> reload.permission(IntegrationPermissions.ADMIN)
                        .description(TextRef.translated("integration.settings.reload", "Reload configuration and translations"))
                        .execute(this::reload))
                .subcommand("demo", demo -> demo.permission(IntegrationPermissions.USE).playerOnly()
                        .description("Demonstrate delayed and asynchronous scheduler tasks")
                        .subcommand("delayed", delayed -> delayed.execute(this::delayed))
                        .subcommand("async", async -> async.execute(this::async)))
                .subcommand("typed", typed -> typed.required(NATIVE_VALUE)
                        .execute(context -> context.sender().sendMessage(Component.text(
                                "MDK_TYPED_" + context.get(NATIVE_VALUE)))))
        ).build();
    }

    private void open(CommandContext context) {
        if (!(context.sender() instanceof BukkitCommandSender bridge)
                || !(bridge.bukkitSender() instanceof Player player)) {
            send(context, "integration.command.player-only", "This command must be used by a player.");
            return;
        }
        if (!player.hasPermission(IntegrationPermissions.USE.node())) {
            send(player, "integration.command.denied", "You do not have permission to use this menu.");
            return;
        }
        lifecycle.get(IntegrationServices.MENUS).manager().open(player, lifecycle.get(IntegrationServices.MENUS).main());
    }

    private void status(CommandContext context) {
        var menus = lifecycle.get(IntegrationServices.MENUS);
        var config = lifecycle.get(IntegrationServices.CONFIG);
        context.sender().sendMessage(Component.text("Lifecycle: " + lifecycle.state()
                + " | config: " + (config.store().isDirty() ? "DIRTY" : "CLEAN")
                + " | menus: " + menus.manager().openSessionCount()
                + " | online: " + Bukkit.getOnlinePlayers().size()));
        lifecycle.diagnostics().components().forEach((name, state) ->
                context.sender().sendMessage(Component.text(" - " + name + ": " + state)));
    }

    private void save(CommandContext context) {
        var result = lifecycle.get(IntegrationServices.CONFIG).save(
                com.trickshotmlg.devkit.config.yaml.YamlSaveOptions.safeRewrite());
        if (result.success()) send(context, "integration.command.save-success", "Configuration saved.");
        else if (result.conflict()) send(context, "integration.command.save-conflict",
                "Configuration file changed externally; no overwrite was made.");
        else send(context, "integration.command.save-failure", "Configuration save failed: {error}",
                Map.of("error", result.error().orElse("unknown error")));
        lifecycle.get(IntegrationServices.MENUS).refreshAll();
    }

    private void reload(CommandContext context) {
        var result = lifecycle.reload();
        lifecycle.get(IntegrationServices.MENUS).refreshAll();
        if (result.successful()) send(context, "integration.command.reload-success",
                "Reload completed. Attempted {count} reload hooks.", Map.of("count", result.attempted().size()));
        else send(context, "integration.command.reload-partial",
                "Reload completed with {count} failure(s); successful changes were kept.",
                Map.of("count", result.failures().size()));
        result.failures().forEach(failure -> plugin.getLogger().log(Level.SEVERE,
                "Lifecycle reload failed in " + failure.componentName(), failure));
    }

    private void delayed(CommandContext context) {
        Player player = player(context);
        if (player == null) return;
        UUID id = player.getUniqueId();
        lifecycle.get(IntegrationServices.FEATURES).scope().sync().later(
                com.trickshotmlg.devkit.scheduler.TickDelay.of(40), () -> {
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) send(online, "integration.command.delayed", "Delayed task ran on the server thread.");
                });
        send(player, "integration.command.scheduled", "Scheduled a message for 40 ticks from now.");
    }

    private void async(CommandContext context) {
        Player player = player(context);
        if (player == null) return;
        UUID id = player.getUniqueId();
        var scheduler = lifecycle.get(IntegrationServices.SCHEDULER);
        lifecycle.get(IntegrationServices.FEATURES).scope().async().submit(() -> {
            long total = 0;
            for (int value = 1; value <= 100_000; value++) total += value;
            return total;
        }).thenSync(result -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null) send(online, "integration.command.async-result",
                    "Background calculation result: {result}", Map.of("result", result));
        }).onFailure(error -> plugin.getLogger().log(Level.SEVERE, "Integration async calculation failed", error));
        send(player, "integration.command.async-started", "Started a background calculation.");
    }

    private Player player(CommandContext context) {
        if (context.sender() instanceof BukkitCommandSender bridge
                && bridge.bukkitSender() instanceof Player player) return player;
        send(context, "integration.command.player-only", "This command must be used by a player.");
        return null;
    }

    private void send(CommandContext context, String key, String fallback) {
        context.sender().sendMessage(text.resolve(TextRef.translated(key, fallback), context.sender().locale()));
    }
    private void send(CommandContext context, String key, String fallback, Map<String, ?> placeholders) {
        context.sender().sendMessage(text.resolve(TextRef.translated(key, fallback), context.sender().locale(), placeholders));
    }
    private void send(Player player, String key, String fallback) {
        IntegrationMessages.send(player, text.resolve(TextRef.translated(key, fallback), locale(player)));
    }
    private void send(Player player, String key, String fallback, Map<String, ?> placeholders) {
        IntegrationMessages.send(player, text.resolve(TextRef.translated(key, fallback), locale(player), placeholders));
    }
    @SuppressWarnings("deprecation")
    private static Locale locale(Player player) { return Locale.forLanguageTag(player.getLocale().replace('_', '-')); }
}
