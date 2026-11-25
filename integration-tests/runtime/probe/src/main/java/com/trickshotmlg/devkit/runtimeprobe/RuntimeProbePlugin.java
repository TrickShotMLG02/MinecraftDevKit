package com.trickshotmlg.devkit.runtimeprobe;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter;
import com.trickshotmlg.devkit.bukkit.gui.BukkitGuiPlatformExtension;
import com.trickshotmlg.devkit.bukkit.gui.GuiButton;
import com.trickshotmlg.devkit.bukkit.gui.GuiManager;
import com.trickshotmlg.devkit.bukkit.gui.GuiMenu;
import com.trickshotmlg.devkit.bukkit.gui.GuiPlatformCapabilities;
import com.trickshotmlg.devkit.bukkit.gui.ItemBuilder;
import com.trickshotmlg.devkit.bukkit.lifecycle.BukkitLifecycle;
import com.trickshotmlg.devkit.bukkit.scheduler.BukkitTaskScheduler;
import com.trickshotmlg.devkit.commands.Arguments;
import com.trickshotmlg.devkit.commands.Argument;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import com.trickshotmlg.devkit.config.ConfigKey;
import com.trickshotmlg.devkit.config.ConfigSchema;
import com.trickshotmlg.devkit.config.yaml.YamlConfigHandle;
import com.trickshotmlg.devkit.config.yaml.YamlSaveOptions;
import com.trickshotmlg.devkit.lifecycle.LifecycleException;
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.lifecycle.ServiceKey;
import com.trickshotmlg.devkit.localization.LocalizationService;
import com.trickshotmlg.devkit.localization.yaml.YamlTranslationSource;
import com.trickshotmlg.devkit.scheduler.AsyncTaskResult;
import com.trickshotmlg.devkit.scheduler.TaskHandle;
import com.trickshotmlg.devkit.scheduler.TaskScope;
import com.trickshotmlg.devkit.scheduler.TaskState;
import com.trickshotmlg.devkit.scheduler.TickDelay;
import com.trickshotmlg.devkit.scheduler.TickPeriod;
import com.trickshotmlg.devkit.storage.SqlParameters;
import com.trickshotmlg.devkit.storage.Storage;
import com.trickshotmlg.devkit.storage.migrations.StorageMigrations;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/** Test-only probe. It runs real MinecraftDevKit APIs inside the selected server process. */
public final class RuntimeProbePlugin extends JavaPlugin {
    private static final ServiceKey<ProbeResource> RESOURCE = ServiceKey.of("runtime-resource", ProbeResource.class);
    private static final Argument<Integer> NESTED_VALUE = Arguments.integer("value", 1, 99);
    private static final ConfigKey<Integer> CONFIG_VALUE = ConfigKey.integer("probe.value", 7).range(0, 100).build();
    private static final ConfigSchema CONFIG_SCHEMA = ConfigSchema.builder().version(1).register(CONFIG_VALUE).build();

    private final CopyOnWriteArrayList<RuntimeCheck> checks = new CopyOnWriteArrayList<>();
    private GuiManager guiManager;
    private BukkitTaskScheduler scheduler;
    private TaskScope scope;
    private BukkitLifecycle bukkitLifecycle;
    private LifecycleManager lifecycle;
    private ProbeResource ownedResource;
    private YamlConfigHandle config;
    private LocalizationService localization;
    private Storage storage;
    private volatile boolean storageHandoffComplete;

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        check("dependent examples enabled", enabled("MinecraftDevKitIntegrationExample")
                        && enabled("MinecraftDevKitStorageExample"),
                "Both standalone examples are plugin.yml dependencies of this probe.");
        verifyCommonClassLoading();
        verifyGuiRuntime();
        verifyLifecycleRuntime();
        verifyConfigurationAndLocalization();
        verifyCommandRuntime();
        verifySchedulerRuntime();
        verifyStorageRuntime();
        writeReport();
    }

    private boolean enabled(String name) {
        Plugin plugin = getServer().getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    private void verifyCommonClassLoading() {
        try {
            Class.forName("com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter", false, getClassLoader());
            Class.forName("com.trickshotmlg.devkit.bukkit.gui.GuiManager", false, getClassLoader());
            Class.forName("com.trickshotmlg.devkit.bukkit.scheduler.BukkitTaskScheduler", false, getClassLoader());
            Class.forName("com.trickshotmlg.devkit.bukkit.lifecycle.BukkitLifecycle", false, getClassLoader());
            check("common adapter classloading", true, "Commands, GUI, scheduler, and lifecycle classes loaded from the probe's server classpath.");
        } catch (LinkageError | ClassNotFoundException failure) {
            check("common adapter classloading", false, failure.toString());
        }
    }

    private void verifyGuiRuntime() {
        try {
            boolean expectedPaper = "paper".equalsIgnoreCase(System.getProperty("mdk.runtime.platform", ""));
            boolean nativeComponents = GuiPlatformCapabilities.supportsNativeAdventureComponents();
            long providers = ServiceLoader.load(BukkitGuiPlatformExtension.class, getClassLoader()).stream().count();
            check("GUI extension discovery", nativeComponents == expectedPaper && providers == (expectedPaper ? 1 : 0),
                    "expectedPaper=" + expectedPaper + ", nativeComponents=" + nativeComponents + ", providers=" + providers);

            guiManager = GuiManager.create(this);
            ItemStack built = ItemBuilder.of(Material.DIAMOND)
                    .name(Component.text("Runtime Probe", net.kyori.adventure.text.format.NamedTextColor.GREEN))
                    .lore(List.of(Component.text("Unicode: 測試")))
                    .itemModel(new NamespacedKey(this, "runtime_probe"))
                    .build();
            check("common ItemBuilder metadata", built.getType() == Material.DIAMOND && built.hasItemMeta(),
                    "Name, lore, and common item-model metadata were applied by Bukkit ItemMeta.");

            GuiMenu menu = GuiMenu.chest(3).title(Component.text("Runtime Probe"))
                    .button(13, GuiButton.of(ItemBuilder.of(Material.EMERALD).name("Button").build(), context -> { }))
                    .build();
            check("GUI menu construction", menu.size() == 27, "Constructed a common chest menu and button.");

            InventoryHolder holder = () -> null;
            if (expectedPaper) {
                BukkitGuiPlatformExtension extension = ServiceLoader.load(BukkitGuiPlatformExtension.class, getClassLoader())
                        .findFirst().orElseThrow();
                Inventory inventory = extension.createInventory(holder, 9, Component.text("Native runtime title"));
                check("Paper native component inventory", inventory != null && inventory.getSize() == 9,
                        "Paper extension created an inventory using the Adventure title overload.");
            } else {
                Inventory inventory = Bukkit.createInventory(holder, 9,
                        LegacyComponentSerializer.legacySection().serialize(Component.text("Spigot runtime title")));
                check("Spigot string-title inventory", inventory != null && inventory.getSize() == 9,
                        "Spigot common inventory title path created a real server inventory.");
            }
        } catch (Throwable failure) {
            check("GUI runtime setup", false, failure.toString());
            getLogger().log(Level.SEVERE, "GUI runtime probe failed", failure);
        }
    }

    private void verifyLifecycleRuntime() {
        try {
            ownedResource = new ProbeResource();
            lifecycle = LifecycleManager.builder().service(RESOURCE, context -> ownedResource).build();
            bukkitLifecycle = BukkitLifecycle.create(this, lifecycle);
            bukkitLifecycle.start();
            check("Bukkit lifecycle start", lifecycle.state().name().equals("RUNNING")
                            && lifecycle.get(RESOURCE) == ownedResource && Bukkit.isPrimaryThread(),
                    "Typed service started on the primary server thread.");

            AtomicInteger rolledBack = new AtomicInteger();
            ServiceKey<ProbeResource> acquired = ServiceKey.of("rollback-resource", ProbeResource.class);
            ServiceKey<String> failureKey = ServiceKey.of("expected-failure", String.class);
            LifecycleManager failing = LifecycleManager.builder()
                    .service(acquired, context -> new ProbeResource(rolledBack))
                    .service(failureKey, context -> { throw new IllegalStateException("intentional runtime rollback probe"); }, acquired)
                    .build();
            try {
                failing.start();
                check("lifecycle startup rollback", false, "The intentional failure unexpectedly started.");
            } catch (LifecycleException expected) {
                failing.close();
                check("lifecycle startup rollback", rolledBack.get() == 1,
                        "The primary startup failure rolled back the acquired resource exactly once.");
            }
        } catch (Throwable failure) {
            check("Bukkit lifecycle start", false, failure.toString());
            getLogger().log(Level.SEVERE, "Lifecycle runtime probe failed", failure);
        }
    }

    private void verifyConfigurationAndLocalization() {
        try {
            config = YamlConfigHandle.createIfMissing(getDataFolder().toPath().resolve("probe.yml"), CONFIG_SCHEMA);
            int cycle = Integer.getInteger("mdk.runtime.cycle", 1);
            boolean expectedExistingValue = config.store().get(CONFIG_VALUE) == (cycle == 1 ? 7 : 19);
            config.store().set(CONFIG_VALUE, 19);
            boolean saved = config.save(YamlSaveOptions.safeRewrite()).success();
            boolean reloaded = config.reload().success() && config.store().get(CONFIG_VALUE) == 19;
            check("typed YAML configuration and restart persistence", expectedExistingValue && saved && reloaded && Files.exists(config.file()),
                    "Cycle " + cycle + " saw " + (cycle == 1 ? "generated default" : "persisted value") + ", then saved and reloaded 19.");

            localization = LocalizationService.builder()
                    .source(YamlTranslationSource.bundled("lang", "en", "de"))
                    .defaultFallbackLocale(Locale.ENGLISH).defaultFallbackEnabled(true).build();
            boolean loaded = localization.reload().successful();
            String resolved = LegacyComponentSerializer.legacySection().serialize(localization.resolve(
                    TextRef.translated("runtime.ready"), Locale.ENGLISH, Map.of("name", "Runtime")));
            check("YAML localization and placeholder", loaded && resolved.contains("Ready Runtime"), resolved);
        } catch (Throwable failure) {
            check("configuration and localization", false, failure.toString());
            getLogger().log(Level.SEVERE, "Configuration/localization runtime probe failed", failure);
        }
    }

    private void verifyStorageRuntime() {
        try {
            Path file = getDataFolder().toPath().resolve("runtime.sqlite");
            storage = Storage.builder().sqlite(file).build();
            var migration = StorageMigrations.builder(storage).locations("classpath:db/migration/runtime").migrate();
            int cycle = Integer.getInteger("mdk.runtime.cycle", 1);
            String migrated = storage.queryOne("SELECT value FROM runtime_probe WHERE id = ?",
                    SqlParameters.of(1), row -> row.getString("value")).orElse("");
            check("SQLite JDBC and restart-persistent Flyway migration", migration.migrationsExecuted() == (cycle == 1 ? 1 : 0)
                            && migration.currentVersion().orElse("").equals("1")
                            && migrated.equals("migration-ready") && Files.exists(file),
                    "Cycle " + cycle + " ran " + migration.migrationsExecuted() + " migration(s); schema and row persisted.");

            storage.async().queryOne("SELECT value FROM runtime_probe WHERE id = ?", SqlParameters.of(1),
                    row -> row.getString("value")).result().whenComplete((value, error) -> {
                try {
                    scope.sync().run(() -> {
                        storageHandoffComplete = true;
                        check("async storage to server-thread handoff", error == null && Bukkit.isPrimaryThread()
                                        && value != null && value.orElse("").equals("migration-ready"),
                                error == null ? "SQLite result was handed back through the Bukkit scheduler." : error.toString());
                        writeReport();
                    });
                } catch (IllegalStateException closedScope) {
                    check("async storage to server-thread handoff", false, "TaskScope closed before continuation: " + closedScope);
                    writeReport();
                }
            });
        } catch (Throwable failure) {
            check("SQLite storage and migrations", false, failure.toString());
            getLogger().log(Level.SEVERE, "Storage runtime probe failed", failure);
        }
    }

    private void verifyCommandRuntime() {
        try {
            var definition = CommandRegistry.builder().root("mdkruntimeprobe", root -> root
                    .execute(context -> context.sender().sendMessage(Component.text(statusLine())))
                    .subcommand("status", status -> status.execute(context ->
                            context.sender().sendMessage(Component.text(statusLine()))))
                    .subcommand("nested", nested -> nested.required(NESTED_VALUE)
                            .execute(context -> context.sender().sendMessage(Component.text("MDK_RUNTIME_NESTED_" + context.get(NESTED_VALUE)))))
                    ).build();
            new BukkitCommandAdapter(this, new StaticTextProvider(), Locale.ENGLISH).register(definition);
            check("Cloud Bukkit command registration", true, "Registered root, nested argument, and status commands during onEnable.");
        } catch (Throwable failure) {
            check("Cloud Bukkit command registration", false, failure.toString());
            getLogger().log(Level.SEVERE, "Command runtime probe failed", failure);
        }
    }

    private void verifySchedulerRuntime() {
        try {
            scheduler = BukkitTaskScheduler.create(this);
            scope = scheduler.scope();
            scope.sync().run(() -> check("sync task primary-thread ownership", Bukkit.isPrimaryThread(),
                    "Sync scheduler callback executed on Bukkit primary thread."));

            AsyncTaskResult<String> async = scope.async().submit(() -> Thread.currentThread().getName());
            async.thenSync(worker -> check("async-to-sync scheduler continuation",
                    !worker.equals(Thread.currentThread().getName()) && Bukkit.isPrimaryThread(),
                    "Worker thread=" + worker + "; continuation ran on the primary thread."))
                    .onFailure(error -> check("async-to-sync scheduler continuation", false, error.toString()));

            TaskHandle cancelled = scope.sync().later(TickDelay.of(200),
                    () -> check("cancelled delayed task suppression", false, "A cancelled task executed."));
            boolean accepted = cancelled.cancel();
            check("task cancellation", accepted && cancelled.state() == TaskState.CANCELLED,
                    "A queued delayed task was cancelled before its due tick.");

            AtomicInteger repeatCount = new AtomicInteger();
            TaskHandle[] repeating = new TaskHandle[1];
            repeating[0] = scope.sync().repeat(TickDelay.of(1), TickPeriod.of(1), context -> {
                if (repeatCount.incrementAndGet() >= 2) {
                    context.cancel();
                    check("repeating task lifecycle", true, "Repeating task ran and cancelled itself after two ticks.");
                    writeReport();
                }
            });
        } catch (Throwable failure) {
            check("Bukkit scheduler runtime", false, failure.toString());
            getLogger().log(Level.SEVERE, "Scheduler runtime probe failed", failure);
        }
    }

    private String statusLine() {
        long passed = checks.stream().filter(check -> check.status().equals("PASS")).count();
        long failed = checks.stream().filter(check -> check.status().equals("FAIL")).count();
        return "MDK_RUNTIME_PROBE checks pass=" + passed + " fail=" + failed + " total=" + checks.size();
    }

    private void check(String name, boolean passed, String detail) {
        RuntimeCheck check = new RuntimeCheck(name, passed ? "PASS" : "FAIL", detail);
        checks.add(check);
        if (passed) getLogger().info("[MDK-RUNTIME] PASS " + name + " — " + detail);
        else getLogger().severe("[MDK-RUNTIME] FAIL " + name + " — " + detail);
    }

    private void writeReport() {
        String json = "{\"platform\":\"" + escape(System.getProperty("mdk.runtime.platform", "unknown"))
                + "\",\"checks\":[" + String.join(",", checks.stream().map(RuntimeCheck::json).toList()) + "]}";
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("runtime-results.json"), json);
        } catch (IOException failure) {
            getLogger().log(Level.SEVERE, "Could not write runtime probe report", failure);
        }
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

    @Override public void onDisable() {
        if (bukkitLifecycle != null) {
            try {
                bukkitLifecycle.close();
                check("lifecycle shutdown and owned resource cleanup", lifecycle.state().name().equals("STOPPED")
                                && ownedResource.closeCount.get() == 1,
                        "Lifecycle manager closed its resource once on plugin disable.");
            } catch (Throwable failure) { check("lifecycle shutdown", false, failure.toString()); }
        }
        if (scope != null) {
            try { scope.close(); check("TaskScope shutdown", true, "TaskScope closed during plugin disable."); }
            catch (Throwable failure) { check("TaskScope shutdown", false, failure.toString()); }
        }
        if (scheduler != null) {
            try { scheduler.close(); check("Bukkit scheduler shutdown", scheduler.isClosed(), "Scheduler closed during plugin disable."); }
            catch (Throwable failure) { check("Bukkit scheduler shutdown", false, failure.toString()); }
        }
        if (guiManager != null) {
            try { guiManager.close(); check("GUI manager shutdown", true, "GUI manager closed during plugin disable."); }
            catch (Throwable failure) { check("GUI manager shutdown", false, failure.toString()); }
        }
        if (storage != null) {
            try { storage.close(); check("SQLite storage shutdown", storage.isClosed(), "SQLite pool and workers closed during plugin disable."); }
            catch (Throwable failure) { check("SQLite storage shutdown", false, failure.toString()); }
        }
        if (config != null) config.close();
        if (localization != null) localization.close();
        if (!storageHandoffComplete) check("async storage result completion", false, "Storage future did not reach the server-thread continuation before disable.");
        writeReport();
        getLogger().info("[MDK-RUNTIME] " + statusLine());
    }

    private static final class ProbeResource implements AutoCloseable {
        private final AtomicInteger closeCount;
        private ProbeResource() { this(new AtomicInteger()); }
        private ProbeResource(AtomicInteger closeCount) { this.closeCount = closeCount; }
        @Override public void close() { closeCount.incrementAndGet(); }
    }
}
