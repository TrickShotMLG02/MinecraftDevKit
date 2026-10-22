package com.trickshotmlg.devkit.examples.lifecycle;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter;
import com.trickshotmlg.devkit.bukkit.lifecycle.BukkitLifecycle;
import com.trickshotmlg.devkit.bukkit.scheduler.BukkitTaskScheduler;
import com.trickshotmlg.devkit.lifecycle.LifecycleContext;
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.scheduler.TaskScope;
import com.trickshotmlg.devkit.scheduler.TickDelay;
import com.trickshotmlg.devkit.scheduler.TickPeriod;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

import static com.trickshotmlg.devkit.examples.lifecycle.LifecycleServices.*;

/** Standalone example of declarative services with dependency ordering and managed resources. */
public final class LifecycleExamplePlugin extends JavaPlugin {
    private LifecycleManager manager;
    private BukkitLifecycle lifecycle;

    @Override public void onEnable() {
        manager = LifecycleManager.builder()
                .service(CONFIG, context -> new ExampleConfigService(getDataFolder().toPath()))
                .service(DATABASE, context -> new ExampleDatabaseService(context.require(CONFIG), getLogger()), CONFIG)
                .service(SCHEDULER, context -> BukkitTaskScheduler.create(this))
                .service(FEATURE, context -> createFeature(context), DATABASE, SCHEDULER)
                .reload(CONFIG, context -> {
                    ExampleConfigService current = context.service(CONFIG);
                    current.reload();
                    getLogger().info("Configuration reload hook completed (reload count=" + current.reloadCount() + ")");
                })
                .reload(FEATURE, context -> getLogger().info(context.service(FEATURE).status()))
                .build();
        lifecycle = BukkitLifecycle.create(this, manager);
        lifecycle.start();
        new BukkitCommandAdapter(this, new StaticTextProvider(), java.util.Locale.ENGLISH)
                .register(LifecycleExampleCommands.create(this));
    }

    private ExampleFeatureService createFeature(LifecycleContext context) {
        getLogger().info("Starting example feature using " + context.require(DATABASE).status());
        context.onClose(() -> getLogger().info("Example feature cleanup callback ran"));
        BukkitTaskScheduler scheduler = context.require(SCHEDULER);
        TaskScope scope = context.manage(scheduler.scope());
        scope.sync().repeat(TickDelay.of(100), TickPeriod.of(1200),
                () -> getLogger().fine("Lifecycle-owned example task is active"));
        return new ExampleFeatureService(context.require(DATABASE));
    }

    void showStatus(com.trickshotmlg.devkit.commands.CommandSender sender) {
        var diagnostics = manager.diagnostics();
        sender.sendMessage(Component.text("Lifecycle: " + diagnostics.state()));
        sender.sendMessage(Component.text("Startup order: " + String.join(" -> ", diagnostics.startupOrder())));
        sender.sendMessage(Component.text("Services: " + diagnostics.components()));
    }

    void showServices(com.trickshotmlg.devkit.commands.CommandSender sender) {
        for (String name : manager.startupOrder()) {
            sender.sendMessage(Component.text(name + " = " + manager.serviceStates().get(name)));
        }
        sender.sendMessage(Component.text("Feature: " + manager.get(FEATURE).status()));
    }

    void reload(com.trickshotmlg.devkit.commands.CommandSender sender) {
        var result = lifecycle.reload();
        sender.sendMessage(Component.text(result.successful()
                ? "Reload hooks completed: " + result.attempted()
                : "Reload was partial; failed components: " + result.failures().stream()
                        .map(failure -> failure.componentName()).toList()));
    }

    @Override public void onDisable() {
        if (lifecycle != null) lifecycle.close();
    }
}
