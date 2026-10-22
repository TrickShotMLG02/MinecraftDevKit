package com.trickshotmlg.devkit.examples.integration;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter;
import com.trickshotmlg.devkit.bukkit.lifecycle.BukkitLifecycle;
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.localization.LocalizationService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Standalone Paper example composing MinecraftDevKit's lifecycle, config, text, permissions, commands, GUI, and scheduler. */
public final class IntegrationExamplePlugin extends JavaPlugin {
    private LifecycleManager manager;
    private BukkitLifecycle lifecycle;

    @Override public void onEnable() {
        manager = IntegrationServices.create(this);
        lifecycle = BukkitLifecycle.create(this, manager);
        lifecycle.start();
        try {
            new BukkitCommandAdapter(this, manager.get(IntegrationServices.TEXT), Locale.ENGLISH)
                    .register(new IntegrationCommands(this, manager, manager.get(IntegrationServices.TEXT)).definition());
        } catch (RuntimeException failure) {
            lifecycle.close();
            throw failure;
        }
        getLogger().info("MinecraftDevKit integration example enabled with services: " + manager.startupOrder());
    }

    @Override public void onDisable() {
        try { if (lifecycle != null) lifecycle.close(); }
        finally { lifecycle = null; manager = null; }
    }
}
