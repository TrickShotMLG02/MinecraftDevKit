package com.trickshotmlg.devkit.bukkit.lifecycle;

import com.trickshotmlg.devkit.lifecycle.LifecycleException;
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.lifecycle.LifecycleReloadResult;
import com.trickshotmlg.devkit.lifecycle.LifecycleState;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/** Thin synchronous lifecycle bridge for a Bukkit/Paper plugin. */
public final class BukkitLifecycle implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final LifecycleManager manager;
    private final BooleanSupplier serverThread;
    private final AtomicBoolean closed = new AtomicBoolean();

    private BukkitLifecycle(Plugin plugin, LifecycleManager manager, BooleanSupplier serverThread, boolean registerListener) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.serverThread = Objects.requireNonNull(serverThread, "serverThread");
        if (registerListener) plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Creates an adapter and closes it automatically when its plugin is disabled.
     * @param plugin plugin that owns the lifecycle and logger
     * @param manager core lifecycle manager to delegate to
     * @return adapter bound to the plugin's traditional server thread
     */
    public static BukkitLifecycle create(Plugin plugin, LifecycleManager manager) {
        return new BukkitLifecycle(plugin, manager, Bukkit::isPrimaryThread, true);
    }

    /** Starts the lifecycle on the Bukkit server thread. Startup failures are logged and rethrown. */
    public void start() {
        requireServerThread("start");
        if (closed.get()) throw new IllegalStateException("Bukkit lifecycle adapter is closed");
        try { manager.start(); }
        catch (LifecycleException failure) {
            plugin.getLogger().log(Level.SEVERE, "Plugin lifecycle startup failed in '"
                    + failure.componentName() + "'", failure);
            throw failure;
        }
    }

    /**
     * Runs registered reload hooks on the Bukkit server thread.
     * @return attempted hooks and individual failures
     */
    public LifecycleReloadResult reload() {
        requireServerThread("reload");
        if (closed.get()) throw new IllegalStateException("Bukkit lifecycle adapter is closed");
        LifecycleReloadResult result = manager.reload();
        for (LifecycleException failure : result.failures()) {
            plugin.getLogger().log(Level.SEVERE, "Plugin lifecycle reload failed in '"
                    + failure.componentName() + "'", failure);
        }
        return result;
    }

    /** The lifecycle manager delegated to by this adapter. */
    public LifecycleManager manager() { return manager; }
    /** Current state of the delegated lifecycle manager. */
    public LifecycleState state() { return manager.state(); }

    /** Closes this adapter when its owning plugin is disabled.
     * @param event plugin disable event
     */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        closeForPlugin(event.getPlugin());
    }

    void closeForPlugin(Plugin disablingPlugin) {
        if (disablingPlugin == plugin) close();
    }

    @Override public void close() {
        requireServerThread("close");
        if (!closed.compareAndSet(false, true)) return;
        HandlerList.unregisterAll(this);
        try { manager.close(); }
        catch (LifecycleException failure) {
            plugin.getLogger().log(Level.SEVERE, "Plugin lifecycle shutdown completed with failures", failure);
            throw failure;
        }
    }

    private void requireServerThread(String operation) {
        if (!serverThread.getAsBoolean()) {
            throw new IllegalStateException("Lifecycle " + operation + " must run on the Bukkit server thread");
        }
    }

    // Package-private test seam avoids pretending an uninitialized mock Bukkit server has real thread affinity.
    static BukkitLifecycle forTest(Plugin plugin, LifecycleManager manager, BooleanSupplier serverThread) {
        return new BukkitLifecycle(plugin, manager, serverThread, false);
    }
}
