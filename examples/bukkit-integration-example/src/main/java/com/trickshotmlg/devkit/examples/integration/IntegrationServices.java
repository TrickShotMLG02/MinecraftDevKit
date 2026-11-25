package com.trickshotmlg.devkit.examples.integration;

import com.trickshotmlg.devkit.bukkit.gui.*;
import com.trickshotmlg.devkit.bukkit.scheduler.BukkitTaskScheduler;
import com.trickshotmlg.devkit.bukkit.events.BukkitEvents;
import com.trickshotmlg.devkit.cooldowns.CooldownManager;
import com.trickshotmlg.devkit.cooldowns.TokenBucketRateLimiter;
import com.trickshotmlg.devkit.config.yaml.YamlConfigHandle;
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.lifecycle.ServiceKey;
import com.trickshotmlg.devkit.localization.LocalizationService;
import com.trickshotmlg.devkit.scheduler.TaskScope;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;

/** Service graph and small application services for the integration example. */
final class IntegrationServices {
    static final ServiceKey<YamlConfigHandle> CONFIG = ServiceKey.of("configuration", YamlConfigHandle.class);
    static final ServiceKey<LocalizationService> TEXT = ServiceKey.of("localization", LocalizationService.class);
    static final ServiceKey<BukkitTaskScheduler> SCHEDULER = ServiceKey.of("scheduler", BukkitTaskScheduler.class);
    static final ServiceKey<Menus> MENUS = ServiceKey.of("menus", Menus.class);
    static final ServiceKey<Features> FEATURES = ServiceKey.of("features", Features.class);

    private IntegrationServices() { }

    static LifecycleManager create(JavaPlugin plugin) {
        var builder = LifecycleManager.builder()
                .service(CONFIG, context -> YamlConfigHandle.createIfMissing(
                        plugin.getDataFolder().toPath().resolve("config.yml"), IntegrationConfig.SCHEMA))
                .service(TEXT, context -> createLocalization(plugin))
                .service(SCHEDULER, context -> BukkitTaskScheduler.create(plugin))
                .service(MENUS, context -> new Menus(plugin, context.require(CONFIG), context.require(TEXT), context),
                        CONFIG, TEXT)
                .service(FEATURES, context -> new Features(plugin, context.require(CONFIG),
                                context.require(MENUS), context.require(TEXT),
                                context.manage(context.require(SCHEDULER).scope()), context),
                        CONFIG, MENUS, TEXT, SCHEDULER);

        builder.reload(CONFIG, context -> {
            var result = context.service(CONFIG).reload();
            if (!result.success()) throw new IllegalStateException(result.error().orElse("Configuration reload failed"));
        });
        builder.reload(TEXT, context -> {
            var result = context.service(TEXT).reload();
            if (!result.successful()) throw new IllegalStateException(result.error().orElse("Localization reload failed"));
        });
        return builder.build();
    }

    private static LocalizationService createLocalization(JavaPlugin plugin) throws IOException {
        var directory = plugin.getDataFolder().toPath().resolve("lang");
        Files.createDirectories(directory);
        plugin.saveResource("lang/en.yml", false);
        plugin.saveResource("lang/de.yml", false);
        LocalizationService service = LocalizationService.builder()
                .source(com.trickshotmlg.devkit.localization.yaml.YamlTranslationSource.bundled("lang", "en", "de"))
                .source(com.trickshotmlg.devkit.localization.yaml.YamlTranslationSource.directory(directory))
                .defaultFallbackLocale(Locale.ENGLISH).defaultFallbackEnabled(true)
                .missingKeyPolicy(com.trickshotmlg.devkit.text.MissingTranslationPolicy.SHOW_MARKER)
                .diagnosticSink(diagnostic -> plugin.getLogger().warning(diagnostic.toString()))
                .watcherFailureHandler(failure -> plugin.getLogger().warning("Localization reload failed: " + failure.getMessage()))
                .build();
        var result = service.reload();
        if (!result.successful()) {
            service.close();
            throw new IllegalStateException(result.error().orElse("Could not load translations"));
        }
        return service;
    }

    static final class Menus {
        private final GuiManager manager;
        private final YamlConfigHandle config;
        private GuiMenu main;
        private GuiMenu players;
        private GuiMenu settings;
        private GuiMenu status;
        private final LocalizationService text;
        private final TokenBucketRateLimiter<UUID> guiLimiter;

        Menus(JavaPlugin plugin, YamlConfigHandle config, LocalizationService text,
              com.trickshotmlg.devkit.lifecycle.LifecycleContext lifecycleContext) {
            this.config = config;
            this.text = text;
            this.guiLimiter = lifecycleContext.manage(new TokenBucketRateLimiter<>(2, 1, Duration.ofSeconds(2)));
            manager = GuiManager.create(plugin, text);
            lifecycleContext.manage(manager);
            ItemStack pane = GuiItems.of(Material.GRAY_STAINED_GLASS_PANE, " ");
            main = GuiMenu.chest(3).title(ref("integration.menu.title"))
                    .fillEmpty(pane)
                    .button(10, GuiButton.of(ctx -> named(Material.PLAYER_HEAD, ctx.text(ref("integration.menu.profile"))), click ->
                            IntegrationMessages.send(click.viewer(), Component.text(click.viewer().getName()))))
                    .button(12, GuiButton.of(localized(Material.PLAYER_HEAD, "integration.menu.players"), click -> click.open(players)))
                    .button(14, GuiButton.builder().item(localized(Material.COMPARATOR, "integration.menu.settings"))
                            .permission(IntegrationPermissions.SETTINGS).onClick(click -> click.open(settings)).build())
                    .button(16, GuiButton.of(localized(Material.BOOK, "integration.menu.status"), click -> click.open(status)))
                    .button(22, GuiButton.of(localized(Material.BARRIER, "integration.menu.close"), GuiClickContext::close))
                    .build();

            players = PagedGui.<Player>chest(6).title(ref("integration.players.title"))
                    .contentSlots(Slots.rectangle(1, 1, 4, 7))
                    .items(context -> List.copyOf(Bukkit.getOnlinePlayers()))
                    .render((context, target) -> GuiButton.of(ctx -> {
                        ItemStack head = GuiItems.playerHead(target);
                        return ItemBuilder.from(head).name(target.getName())
                                .lore(List.of(ctx.text(ref("integration.players.detail"), Map.of("name", target.getName()))))
                                .build();
                    }, click -> click.open(details(target.getUniqueId(), target.getName()))))
                    .previousPage(45)
                    .pageIndicator(49, (context, page) -> ItemBuilder.of(Material.PAPER)
                            .name(context.text(ref("integration.players.page"), Map.of(
                                    "current", page.currentPage(), "total", page.totalPages())))
                            .lore(List.of(context.text(ref("integration.players.count"), Map.of("count", page.totalItems()))))
                            .build())
                    .nextPage(53).navigationTemplates(
                            localized(Material.ARROW, "integration.players.previous"),
                            localized(Material.ARROW, "integration.players.next"),
                            context -> ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                                    .name(context.text(ref("integration.players.unavailable"))).build())
                    .emptyStateTemplate(48, localized(Material.BARRIER, "integration.players.empty"))
                    .refreshOn(PlayerJoinEvent.class, event -> true, RefreshScope.CONTENT)
                    .refreshOn(PlayerQuitEvent.class, event -> true, RefreshScope.CONTENT)
                    .build();

            ToggleButton notifications = ToggleButton.builder().key("integration.notifications")
                    .bind(context -> config.store().get(IntegrationConfig.NOTIFICATIONS),
                            (context, value) -> config.store().set(IntegrationConfig.NOTIFICATIONS, value))
                    .permission(IntegrationPermissions.SETTINGS)
                    .onItem(context -> toggleItem(context, true)).offItem(context -> toggleItem(context, false))
                    .disabledItem(GuiItems.redPane("Permission required")).build();
            RadioGroup<String> presentation = RadioGroup.<String>builder().key("integration.presentation").selected("compact")
                    .option(13, "compact", localized(Material.LIME_DYE, "integration.settings.compact"),
                            localized(Material.GRAY_DYE, "integration.settings.compact"), null, context -> true, null)
                    .option(15, "detailed", localized(Material.GOLD_INGOT, "integration.settings.detailed"),
                            localized(Material.GRAY_DYE, "integration.settings.detailed"), null, context -> true, null)
                    .onChange((click, value) -> IntegrationMessages.send(click.viewer(), text.resolve(
                            ref("integration.settings.view"), Locale.forLanguageTag(
                                    click.viewer().getLocale().replace('_', '-')), Map.of("value", value))))
                    .build();
            settings = GuiMenu.chest(3).title(ref("integration.settings.title"))
                    .background(context -> context.row() == 0 || context.row() == 2
                            ? GuiItems.blackPane() : GuiItems.grayPane())
                    .button(9, GuiButtons.back())
                    .component(11, notifications)
                    .radioGroup(presentation)
                    .button(17, GuiButton.of(context -> config.store().isDirty()
                                    ? named(Material.ORANGE_DYE, context.text(ref("integration.settings.unsaved")))
                                    : named(Material.LIME_DYE, Component.text("Configuration saved")), click -> { }))
                    .button(20, GuiButton.builder().item(localized(Material.EMERALD, "integration.settings.save"))
                            .permission(IntegrationPermissions.SETTINGS).onClick(click -> saveFromGui(click.viewer())).build())
                    .button(24, GuiButton.builder().item(localized(Material.CLOCK, "integration.settings.reload"))
                            .permission(IntegrationPermissions.ADMIN).onClick(click -> {
                                var result = config.reload();
                                IntegrationMessages.send(click.viewer(), Component.text(result.success()
                                        ? "Configuration reloaded." : result.error().orElse("Reload failed; active values were kept.")));
                                manager.refreshOpen(settings);
                            }).build())
                    .build();

            status = GuiMenu.chest(3).title(ref("integration.status.title"))
                    .fillEmpty(pane)
                    .button(11, GuiButton.of(context -> ItemBuilder.of(Material.CLOCK)
                            .name(context.text(ref("integration.status.lifecycle"), Map.of("state", "RUNNING")))
                            .lore(List.of(context.text(ref("integration.status.config"), Map.of("state", config.store().isDirty() ? "DIRTY" : "CLEAN"))))
                    .build(), click -> {
                        if (guiLimiter.tryAcquire(click.viewer().getUniqueId())) click.refresh();
                        else IntegrationMessages.send(click.viewer(), Component.text("Please wait before refreshing again."));
                    }))
                    .button(13, GuiButton.builder().item(context -> ItemBuilder.of(Material.PLAYER_HEAD)
                            .name(context.text(ref("integration.status.online"), Map.of("count", Bukkit.getOnlinePlayers().size())))
                            .build()).visibleWhen(context -> config.store().get(IntegrationConfig.SHOW_ONLINE_COUNT))
                            .onClick(GuiClickContext::refresh).build())
                    .button(22, GuiButtons.back()).build();
        }

        GuiMenu main() { return main; }
        GuiMenu players() { return players; }
        GuiMenu settings() { return settings; }
        GuiMenu status() { return status; }
        GuiManager manager() { return manager; }
        void playerQuit(UUID playerId) { guiLimiter.remove(playerId); }
        void refreshAll() {
            manager.refreshOpen(main); manager.refreshOpen(players); manager.refreshOpen(settings); manager.refreshOpen(status);
        }
        void refreshPlayers() { manager.refreshOpen(players); manager.refreshOpen(main); manager.refreshOpen(status); }
        void refreshSettings() { manager.refreshOpen(settings); }

        private GuiMenu details(UUID id, String name) {
            Player online = Bukkit.getPlayer(id);
            ItemStack head = online == null ? new ItemStack(Material.PLAYER_HEAD) : GuiItems.playerHead(online);
            return GuiMenu.chest(3).title(ref("integration.players.detail-title"))
                    .fillEmpty(GuiItems.blackPane())
                    .item(13, ItemBuilder.from(head)
                            .name(name).lore("UUID: " + id).build())
                    .button(22, GuiButton.of(localized(Material.ARROW, "integration.players.back"), GuiClickContext::back))
                    .build();
        }
        private void saveFromGui(Player player) {
            var result = config.save(com.trickshotmlg.devkit.config.yaml.YamlSaveOptions.safeRewrite());
            String key = result.success() ? "integration.settings.saved"
                    : result.conflict() ? "integration.command.save-conflict" : "integration.command.save-failure";
            String fallback = result.success() ? "Configuration saved."
                    : result.conflict() ? "Configuration changed externally; save was rejected."
                    : "Configuration save failed: {error}";
            Map<String, ?> values = result.success() || result.conflict() ? Map.of()
                    : Map.of("error", result.error().orElse("unknown error"));
            IntegrationMessages.send(player, text.resolve(ref(key, fallback), Locale.forLanguageTag(player.getLocale().replace('_', '-')), values));
            refreshAll();
        }
        private ItemStack toggleItem(GuiRenderContext context, boolean enabled) {
            TextRef state = ref(enabled ? "integration.settings.enabled" : "integration.settings.disabled");
            return ItemBuilder.of(enabled ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)
                    .name(context.text(ref("integration.settings.notifications"), Map.of("state", context.text(state))))
                    .build();
        }
        private static ItemStack named(Material material, Component name) { return ItemBuilder.of(material).name(name).build(); }
        private static GuiItemTemplate localized(Material material, String key) {
            return context -> ItemBuilder.of(material).name(context.text(ref(key))).build();
        }
    }

    static final class Features {
        private final JavaPlugin plugin;
        private final YamlConfigHandle config;
        private final Menus menus;
        private final LocalizationService text;
        private final TaskScope scope;
        private com.trickshotmlg.devkit.scheduler.TaskHandle refreshTask;
        private final CooldownManager<UUID> commandCooldowns;
        Features(JavaPlugin plugin, YamlConfigHandle config, Menus menus, LocalizationService text,
                 TaskScope scope, com.trickshotmlg.devkit.lifecycle.LifecycleContext lifecycleContext) {
            this.plugin = plugin; this.config = config; this.menus = menus; this.text = text;
            this.scope = scope;
            this.commandCooldowns = lifecycleContext.manage(CooldownManager.create());
            BukkitEvents events = lifecycleContext.manage(BukkitEvents.create(plugin));
            events.subscribe(PlayerJoinEvent.class, EventPriority.NORMAL, false, this::onJoin);
            events.subscribe(PlayerQuitEvent.class, this::onQuit);
            lifecycleContext.manage(config.store().onChange(change -> {
                menus.refreshAll();
                if (change.key() == IntegrationConfig.REFRESH_INTERVAL) scheduleRefresh();
            }));
            scheduleRefresh();
        }
        TaskScope scope() { return scope; }
        CooldownManager<UUID> commandCooldowns() { return commandCooldowns; }
        private void scheduleRefresh() {
            if (refreshTask != null) refreshTask.cancel();
            int interval = config.store().get(IntegrationConfig.REFRESH_INTERVAL);
            refreshTask = scope.sync().repeat(Duration.ofSeconds(interval), Duration.ofSeconds(interval),
                    context -> menus.refreshPlayers());
        }
        private void onJoin(PlayerJoinEvent event) {
            menus.refreshPlayers();
            if (config.store().get(IntegrationConfig.NOTIFICATIONS)) {
                Player player = event.getPlayer();
                IntegrationMessages.send(player, text.resolve(ref("integration.events.join"),
                        Locale.forLanguageTag(player.getLocale().replace('_', '-')),
                        Map.of("count", Bukkit.getOnlinePlayers().size())));
            }
        }
        private void onQuit(PlayerQuitEvent event) {
            UUID playerId = event.getPlayer().getUniqueId();
            commandCooldowns.remove(playerId);
            menus.playerQuit(playerId);
            menus.refreshPlayers();
        }
    }

    static TextRef ref(String key) { return TextRef.translated(key); }
    static TextRef ref(String key, String fallback) { return TextRef.translated(key, fallback); }
}
