package com.trickshotmlg.devkit.examples.integration;

import com.trickshotmlg.devkit.bukkit.permissions.BukkitPermissionRegistration;
import com.trickshotmlg.devkit.bukkit.permissions.BukkitPermissions;
import com.trickshotmlg.devkit.bukkit.lifecycle.BukkitLifecycle;
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.localization.LocalizationService;
import org.bukkit.plugin.java.JavaPlugin;


/** Standalone Paper example composing MinecraftDevKit's lifecycle, config, text, permissions, commands, GUI, and scheduler. */
public final class IntegrationExamplePlugin extends JavaPlugin {
    private LifecycleManager manager;
    private BukkitLifecycle lifecycle;
    private BukkitPermissionRegistration permissionRegistration;
    private BukkitPermissionRegistration runtimePermissionRegistration;

    @Override public void onEnable() {
        manager = IntegrationServices.create(this);
        lifecycle = BukkitLifecycle.create(this, manager);
        lifecycle.start();
        try {
            permissionRegistration = BukkitPermissions.register(this, IntegrationPermissions.CATALOG);
            getLogger().info("Runtime permissions registered: " + permissionRegistration.diagnostics());
            runtimePermissionRegistration = BukkitPermissions.register(this, IntegrationPermissions.RUNTIME_CATALOG);
            getLogger().info("Runtime-only permission registration: " + runtimePermissionRegistration.diagnostics());
            IntegrationCommandRegistration.register(this, manager.get(IntegrationServices.TEXT),
                    new IntegrationCommands(this, manager, manager.get(IntegrationServices.TEXT)).definition());
        } catch (RuntimeException failure) {
            if (permissionRegistration != null) permissionRegistration.close();
            if (runtimePermissionRegistration != null) runtimePermissionRegistration.close();
            lifecycle.close();
            throw failure;
        }
        getLogger().info("MinecraftDevKit integration example enabled with services: " + manager.startupOrder());
    }

    @Override public void onDisable() {
        try { if (permissionRegistration != null) permissionRegistration.close(); }
        finally {
            permissionRegistration = null;
            try { if (runtimePermissionRegistration != null) runtimePermissionRegistration.close(); }
            finally {
                runtimePermissionRegistration = null;
                try { if (lifecycle != null) lifecycle.close(); }
                finally { lifecycle = null; manager = null; }
            }
        }
    }
}
