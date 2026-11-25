package com.trickshotmlg.devkit.bukkit.permissions;

import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Owns only the Bukkit permissions newly added by one {@link BukkitPermissions} call. */
public final class BukkitPermissionRegistration implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final PluginManager manager;
    private final BooleanSupplier serverThread;
    private final List<OwnedPermission> owned;
    private final PermissionRegistrationDiagnostics diagnostics;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean listenerRegistered;

    BukkitPermissionRegistration(Plugin plugin, PluginManager manager, BooleanSupplier serverThread,
                                 List<OwnedPermission> owned, PermissionRegistrationDiagnostics diagnostics) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.serverThread = Objects.requireNonNull(serverThread, "serverThread");
        this.owned = List.copyOf(owned);
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    /** Counts and node list for the immutable registration snapshot. */
    public PermissionRegistrationDiagnostics diagnostics() { return diagnostics; }

    /** Nodes this handle actually added; matching pre-existing definitions are not owned. */
    public List<String> ownedNodes() { return owned.stream().map(OwnedPermission::node).toList(); }

    /** Whether this handle has completed cleanup. */
    public boolean isClosed() { return closed.get(); }

    void listenerRegistered() { listenerRegistered = true; }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) close();
    }

    @Override public void close() {
        if (!serverThread.getAsBoolean()) {
            throw new IllegalStateException("Bukkit permission cleanup must run on the server thread");
        }
        if (!closed.compareAndSet(false, true)) return;
        if (listenerRegistered) HandlerList.unregisterAll(this);
        for (int index = owned.size() - 1; index >= 0; index--) {
            OwnedPermission registration = owned.get(index);
            if (manager.getPermission(registration.node()) == registration.permission()) {
                manager.removePermission(registration.permission());
            }
            BukkitPermissions.removeOwner(manager, registration.node(), registration.permission());
        }
    }

    record OwnedPermission(String node, org.bukkit.permissions.Permission permission) { }
}
