package com.trickshotmlg.devkit.bukkit.scheduler;

import com.trickshotmlg.devkit.scheduler.TaskScope;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Creates task scopes keyed by player UUID without retaining Player instances. */
public final class BukkitPlayerTaskScopes implements Listener, AutoCloseable {
    private final Object lock = new Object();
    private final Map<UUID, TaskScope> scopes = new HashMap<>();
    private Plugin plugin;
    private TaskScope owner;
    private final boolean cancelOnQuit;
    private boolean closed;

    private BukkitPlayerTaskScopes(Plugin plugin, TaskScope owner, boolean cancelOnQuit) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.cancelOnQuit = cancelOnQuit;
    }

    /**
     * Registers the quit/disable listener. The returned helper should itself be lifecycle-managed.
     * @param plugin owning plugin
     * @param owner parent scope closed with the plugin/service
     * @param cancelOnQuit whether disconnect closes the player's child scope
     * @return UUID-keyed task scope registry
     */
    public static BukkitPlayerTaskScopes create(Plugin plugin, TaskScope owner, boolean cancelOnQuit) {
        BukkitPlayerTaskScopes result = new BukkitPlayerTaskScopes(plugin, owner, cancelOnQuit);
        try {
            plugin.getServer().getPluginManager().registerEvents(result, plugin);
            return result;
        } catch (RuntimeException failure) {
            result.close();
            throw failure;
        }
    }

    /**
     * Returns a stable child scope for this UUID until it is explicitly closed or the player quits.
     * @param playerId player UUID; no Player instance is retained
     * @return stable child task scope
     */
    public TaskScope forPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (lock) {
            ensureOpen();
            return scopes.computeIfAbsent(playerId, ignored -> owner.childScope());
        }
    }

    /**
     * Closes and removes this player's scope. A later {@link #forPlayer(UUID)} call creates a fresh one.
     * @param playerId player UUID
     * @return true if a scope was removed
     */
    public boolean closePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (lock) {
            TaskScope scope = scopes.remove(playerId);
            if (scope == null) return false;
            scope.close();
            return true;
        }
    }

    /**
     * Returns the number of currently owned player UUID scopes.
     * @return active UUID scope count
     */
    public int activePlayerCount() { synchronized (lock) { return scopes.size(); } }

    /** Handles player disconnect when {@code cancelOnQuit} was enabled. */
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) {
        if (cancelOnQuit) closePlayer(event.getPlayer().getUniqueId());
    }

    /** Closes all owned scopes when this plugin disables. */
    @EventHandler public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) close();
    }

    @Override public void close() {
        Map<UUID, TaskScope> owned;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            owned = new HashMap<>(scopes);
            scopes.clear();
        }
        owned.values().forEach(TaskScope::close);
        HandlerList.unregisterAll(this);
        plugin = null;
        owner = null;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Player task scopes are closed");
    }
}
